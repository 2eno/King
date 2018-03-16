package eu.zipf.zeno.kingo;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.ToggleButton;

import com.google.android.gms.games.multiplayer.realtime.OnRealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import eu.zipf.zeno.kingo.bc.R;

/**
 * Created by zeno on 11.03.18.
 */

public class Multiplayer {
        /*
     * COMMUNICATIONS SECTION. Methods that implement the game's network
     * protocol.
     */


    private static Activity ACTIVITY = new Activity();

    static void setActivity(Activity activity) {
        ACTIVITY = activity;
    }

    // Score of other participants. We update this as we receive their scores
    // from the network.
    static Map<String, Integer> mParticipantScore = new HashMap<>();

    // Participants who sent us their final score.
    static Set<String> mFinishedParticipants = new HashSet<>();


    public static OnRealTimeMessageReceivedListener mOnRealTimeMessageReceivedListener = new OnRealTimeMessageReceivedListener() {
        @Override
        public void onRealTimeMessageReceived(@NonNull RealTimeMessage realTimeMessage) {
            byte[] buf = realTimeMessage.getMessageData();
            String sender = realTimeMessage.getSenderParticipantId();
            String log = "Message recieved: ";
            for (byte b : buf) {
                log += ", " + b;
            }
            Log.d(MainActivity.TAG, log);


            switch (buf[0]) {
                case 10:                                                                            //10 is GAME_RESULT
                    UpdateUI.updateDice(buf);
                    break;
                case 11:
                    MainActivity.myTurn();
                    break;
                default:
                    break;
            }
        }
    };


    static void broadcast(ArrayList<Byte> msg, String mRoomId) {
        PlayAPI.mRealTimeMultiplayerClient.sendUnreliableMessageToOthers(castByte(msg.toArray()), mRoomId);
    }

    static void broadcast(byte[] msg, String mRoomId) {
        PlayAPI.mRealTimeMultiplayerClient.sendUnreliableMessageToOthers(msg, mRoomId);
    }

    static void broadcast(Byte msg, String mRoomId) {
        ArrayList<Byte> l = new ArrayList<Byte>();
        l.add(msg);
        broadcast(l, mRoomId);
    }

    static void broadcastReset(String mRoomId) {
        byte[] toSend = {10, 1, 1, 1, 1, 1, 1};
        broadcast(toSend, mRoomId);
    }

    static byte[] castByte(Object[] o) {
        byte[] ret = new byte[o.length];
        int i = 0;
        for (Object obj : o) {
            ret[i] = (byte) obj;
            i++;
        }
        return ret;
    }

    static byte[] getReroll() {
        ArrayList toCast = new ArrayList();
        for (int i = 0; i < MainActivity.getDice().getSize(); i++) {
            ToggleButton b = ACTIVITY.findViewById(R.id.dice_1 + i);
            if (!b.isChecked()) {
                toCast.add((byte) i);
            }
        }
        byte[] ret = castByte(toCast.toArray());

        return ret;
    }
}

