package eu.zipf.zeno.games.bc;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.OnRealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by zeno on 11.03.18.
 */

public class Multiplayer {

    /*
     * COMMUNICATIONS SECTION. Methods that implement the game's network
     * protocol.
     */

    // Score of other participants. We update this as we receive their scores
    // from the network.
    static Map<String, Integer> mParticipantScore = new HashMap<>();

    // Participants who sent us their final score.
    static Set<String> mFinishedParticipants = new HashSet<>();

    // Called when we receive a real-time message from the network.
    // Messages in our game are made up of 2 bytes: the first one is 'F' or 'U'
    // indicating
    // whether it's a final or interim score. The second byte is the score.
    // There is also the
    // 'S' message, which indicates that the game should start.
    public static OnRealTimeMessageReceivedListener mOnRealTimeMessageReceivedListener = new OnRealTimeMessageReceivedListener() {
        @Override
        public void onRealTimeMessageReceived(@NonNull RealTimeMessage realTimeMessage) {
            byte[] buf = realTimeMessage.getMessageData();
            String sender = realTimeMessage.getSenderParticipantId();
            Log.d(MainActivity.TAG, "Message received: " + (char) buf[0] + "/" + (int) buf[1]);

            if (buf[0] == 'F' || buf[0] == 'U') {
                // score update.
                int existingScore = mParticipantScore.containsKey(sender) ?
                        mParticipantScore.get(sender) : 0;
                int thisScore = (int) buf[1];
                if (thisScore > existingScore) {
                    // this check is necessary because packets may arrive out of
                    // order, so we
                    // should only ever consider the highest score we received, as
                    // we know in our
                    // game there is no way to lose points. If there was a way to
                    // lose points,
                    // we'd have to add a "serial number" to the packet.
                    mParticipantScore.put(sender, thisScore);
                }

                // update the scores on the screen
                UpdateUI.updatePeerScoresDisplay();

                // if it's a final score, mark this participant as having finished
                // the game
                if ((char) buf[0] == 'F') {
                    mFinishedParticipants.add(realTimeMessage.getSenderParticipantId());
                }
            }
        }
    };

    // Broadcast my score to everybody else.
    static void broadcastScore(int health, int energy, int score) {

        // First byte in message indicates whether it's a final score or not
        //mMsgBuf[0] = (byte) (finalScore ? 'F' : 'U');
        MainActivity.mMsgBuf[0] = (byte) health;
        // Second byte is the score.
        MainActivity.mMsgBuf[1] = (byte) energy;

        MainActivity.mMsgBuf[2] = (byte) score;

        // Send to every other participant.
        for (Participant p : MainActivity.mParticipants) {
            if (p.getParticipantId().equals(MainActivity.mMyId)) {
                continue;
            }
            if (p.getStatus() != Participant.STATUS_JOINED) {
                continue;
            }
      /*if (finalScore) {
        // final score notification must be sent via reliable message
        mRealTimeMultiplayerClient.sendReliableMessage(mMsgBuf,
                mRoomId, p.getParticipantId(), new RealTimeMultiplayerClient.ReliableMessageSentCallback() {
                  @Override
                  public void onRealTimeMessageSent(int statusCode, int tokenId, String recipientParticipantId) {
                    Log.d(TAG, "RealTime message sent");
                    Log.d(TAG, "  statusCode: " + statusCode);
                    Log.d(TAG, "  tokenId: " + tokenId);
                    Log.d(TAG, "  recipientParticipantId: " + recipientParticipantId);
                  }
                })
                .addOnSuccessListener(new OnSuccessListener<Integer>() {
                  @Override
                  public void onSuccess(Integer tokenId) {
                    Log.d(TAG, "Created a reliable message with tokenId: " + tokenId);
                  }
                });
      } else {*/
            // it's an interim score notification, so we can use unreliable
            MainActivity.mRealTimeMultiplayerClient.sendUnreliableMessage(MainActivity.mMsgBuf, MainActivity.mRoomId,
                    p.getParticipantId());
            //}
        }
    }
}
