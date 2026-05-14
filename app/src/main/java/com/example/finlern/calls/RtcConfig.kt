package com.example.finlern.calls

import org.webrtc.PeerConnection

/**
 * ICE server configuration.
 *
 * STUN: Google's public STUN servers. Free, used to discover the device's
 *       public IP. Sufficient when both peers are behind cone NATs.
 *
 * TURN: openrelay.metered.ca is a free public TURN relay run by the
 *       Metered.ca folks. Free is fine for early testing but rate-limited
 *       and unreliable for real traffic.
 *
 *       For production:
 *         1. Spin up coturn on a $5 VPS (Hetzner/DigitalOcean/Contabo) and
 *            replace the URLs below.
 *         2. Or use Twilio / Xirsys (paid).
 *         3. Or, if budget really is zero, leave openrelay as a fallback
 *            but expect ~5% of calls in restrictive networks to fail.
 *
 * Why both? About 80-85% of calls go through with STUN only (peer-to-peer).
 * The remaining ~15% need a TURN relay because at least one peer is behind
 * a symmetric NAT or restrictive firewall.
 */
object RtcConfig {

    fun iceServers(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder(
            listOf(
                "turn:openrelay.metered.ca:80",
                "turn:openrelay.metered.ca:443",
                "turn:openrelay.metered.ca:443?transport=tcp"
            )
        )
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer()
    )

    fun rtcConfig(): PeerConnection.RTCConfiguration =
        PeerConnection.RTCConfiguration(iceServers()).apply {
            // BALANCED keeps both UDP and TCP candidates so we have a fallback
            // when UDP is blocked (common on restrictive corporate / mobile
            // carrier networks).
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
            // Unified Plan is the modern default and required for multi-track
            // (audio + video) calls.
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
}
