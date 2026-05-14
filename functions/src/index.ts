// FinLern Cloud Functions.
//
// These run on Firebase's servers using the project's built-in service
// account. They replace the old client-side FCMClient.kt that shipped
// service-account.json inside the APK (which leaked admin credentials to
// anyone who could unzip the apk).
//
// All push notifications are now triggered by Firestore document writes:
//   - chats/{chatId}/messages/{messageId}  -> chat push
//   - calls/{callId}                       -> incoming-call push
//
// The Android client just writes Firestore docs and the rest is handled
// here. No secrets needed on the device.

import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger, setGlobalOptions } from "firebase-functions/v2";

initializeApp();

// Must match Cloud Firestore: your database lives in eu multi-region (`eur3`).
// Gen-2 triggers are created there; pinning the executor to europe-west4
// avoids cross-region Eventarc hops and first-deploy IAM edge cases that
// often hit us-central1 + eur3 mismatches.
// https://firebase.google.com/docs/functions/locations#firestore_and_storage_triggers
setGlobalOptions({ region: "europe-west4", maxInstances: 10 });

// ------- Helpers -----------------------------------------------------------

interface UserProfile {
  email?: string;
  name?: string;
  fcmToken?: string;
}

async function loadProfile(email: string): Promise<UserProfile | null> {
  const snap = await getFirestore().collection("userProfiles").doc(email).get();
  return snap.exists ? (snap.data() as UserProfile) : null;
}

async function clearStaleToken(email: string): Promise<void> {
  try {
    await getFirestore()
      .collection("userProfiles")
      .doc(email)
      .update({ fcmToken: FieldValue.delete() });
    logger.info(`Cleared stale FCM token for ${email}`);
  } catch (e) {
    logger.error(`Failed clearing stale token for ${email}`, e);
  }
}

function isStaleTokenError(err: unknown): boolean {
  const code = (err as { errorInfo?: { code?: string } })?.errorInfo?.code ?? "";
  return (
    code === "messaging/registration-token-not-registered" ||
    code === "messaging/invalid-registration-token" ||
    code === "messaging/invalid-argument"
  );
}

// ------- Chat message push -------------------------------------------------

export const onMessageCreated = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const snap = event.data;
    if (!snap) {
      logger.warn("onMessageCreated fired with no snapshot");
      return;
    }
    const message = snap.data() as {
      senderId?: string;
      content?: string;
      type?: string;
      metadata?: Record<string, string>;
    };
    const chatId = event.params.chatId;
    const senderId = message.senderId;
    if (!senderId) {
      logger.warn(`Message ${snap.id} has no senderId, skipping`);
      return;
    }

    const chatSnap = await getFirestore().collection("chats").doc(chatId).get();
    if (!chatSnap.exists) {
      logger.warn(`Chat ${chatId} not found, skipping push`);
      return;
    }
    const participants: string[] = (chatSnap.get("participants") as string[]) || [];
    const recipient = participants.find((p) => p !== senderId);
    if (!recipient) {
      logger.warn(`No recipient found in chat ${chatId}, skipping push`);
      return;
    }

    const recipientProfile = await loadProfile(recipient);
    const token = recipientProfile?.fcmToken;
    if (!token) {
      logger.info(`No FCM token stored for ${recipient}, skipping push`);
      return;
    }

    const senderProfile = await loadProfile(senderId);
    const senderName = senderProfile?.name || senderId;

    let body = message.content || "New message";
    if (message.type === "IMAGE") body = "📷 Sent you an image";
    else if (message.type === "FILE") {
      const fileName = message.metadata?.["fileName"];
      body = fileName ? `📎 ${fileName}` : "📎 Sent you a file";
    }

    try {
      await getMessaging().send({
        token,
        notification: {
          title: `New message from ${senderName}`,
          body,
        },
        data: {
          type: "chat_message",
          senderId,
          chatId,
          message: body,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "chat_messages",
            sound: "default",
          },
        },
      });
      logger.info(`Chat push delivered to ${recipient}`);
    } catch (err) {
      if (isStaleTokenError(err)) {
        logger.warn(`Stale FCM token for ${recipient}`, err);
        await clearStaleToken(recipient);
      } else {
        logger.error(`Chat push failed for ${recipient}`, err);
      }
    }
  }
);

// ------- Incoming call push ------------------------------------------------

export const onCallCreated = onDocumentCreated(
  "calls/{callId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const call = snap.data() as {
      callerId?: string;
      receiverId?: string;
      type?: string;
      status?: string;
    };
    const callId = event.params.callId;
    const callerId = call.callerId;
    const receiverId = call.receiverId;
    const callType = call.type || "audio";
    if (!callerId || !receiverId) {
      logger.warn(`Call ${callId} missing caller/receiver, skipping`);
      return;
    }
    // Only fire when the call is first ringing. If someone re-uses a call
    // doc later (status updates), the trigger won't run again because this
    // is onCreate, but guard anyway.
    if (call.status && call.status !== "ringing") {
      logger.info(`Call ${callId} created in status ${call.status}, skipping push`);
      return;
    }

    const receiverProfile = await loadProfile(receiverId);
    const token = receiverProfile?.fcmToken;
    if (!token) {
      logger.info(`No FCM token for ${receiverId}, can't ring`);
      return;
    }

    const callerProfile = await loadProfile(callerId);
    const callerName = callerProfile?.name || callerId;

    try {
      // Data-only push. The Android service builds a full-screen incoming
      // call activity itself instead of letting the system show a normal
      // notification — that's how WhatsApp/Telegram do it.
      await getMessaging().send({
        token,
        data: {
          type: "incoming_call",
          callId,
          callerId,
          callerName,
          callType,
        },
        android: {
          priority: "high",
          // 60s ringer timeout. If the recipient's device is offline
          // longer than this, FCM drops the message — better than them
          // picking up a missed call from 10 minutes ago.
          ttl: 60_000,
        },
      });
      logger.info(`Call push delivered to ${receiverId} for call ${callId}`);
    } catch (err) {
      if (isStaleTokenError(err)) {
        logger.warn(`Stale FCM token for ${receiverId}`, err);
        await clearStaleToken(receiverId);
      } else {
        logger.error(`Call push failed for ${receiverId}`, err);
      }
    }
  }
);
