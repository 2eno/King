package eu.zipf.zeno.kingo;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import eu.zipf.zeno.kingo.bc.R;

/**
 * Created by 2eno on 11.03.18.
 */

public final class UpdateUI {

    static Activity ACTIVITY = new Activity();

    static void setActivity(Activity activity) {
        ACTIVITY = activity;
    }

    /*
     * UI SECTION. Methods that implement the game's UI.
     */

    // This array lists everything that's clickable, so we can install click
    // event handlers.
    final static int[] CLICKABLES = {
            R.id.button_accept_popup_invitation, R.id.button_invite_players,
            R.id.button_quick_game, R.id.button_see_invitations, R.id.button_sign_in,
            R.id.button_sign_out
    };

    // This array lists all the individual screens our game has.
    final static int[] SCREENS = {
            R.id.screen_game, R.id.screen_main, R.id.screen_sign_in,
            R.id.screen_wait
    };
    static int mCurScreen = -1;

    static int[] getClickables() {
        return CLICKABLES;
    }

    static void switchToScreen(int screenId, PlayAPI playAPI) {
        // make the requested screen visible; hide all others.
        for (int id : SCREENS) {
            ACTIVITY.findViewById(id).setVisibility(screenId == id ? View.VISIBLE : View.GONE);
        }
        mCurScreen = screenId;

        // should we show the invitation popup?
        boolean showInvPopup;
        if (playAPI.mIncomingInvitationId == null) {
            // no invitation, so no popup
            showInvPopup = false;

        } else {
            // single-player: show on main screen and gameplay screen
            showInvPopup = (mCurScreen == R.id.screen_main);
        }
        ACTIVITY.findViewById(R.id.invitation_popup).setVisibility(showInvPopup ? View.VISIBLE : View.GONE);
    }

    static void switchToMainScreen(PlayAPI playAPI) {
        if (playAPI.mRealTimeMultiplayerClient != null) {
            switchToScreen(R.id.screen_main, playAPI);
        } else {
            switchToScreen(R.id.screen_sign_in, playAPI);
        }
    }

    // updates the label that shows my score
    static void updateScoreDisplay() {
        ((TextView) ACTIVITY.findViewById(R.id.my_score)).setText("Test");
    }


    // updates the screen with the scores from our peers
    static void updatePeerScoresDisplay() {

    }
    static void forMyTurn() {
        ACTIVITY.findViewById(R.id.button_next).setVisibility(View.GONE);
        ACTIVITY.findViewById(R.id.button_roll).setEnabled(true);
        ACTIVITY.findViewById(R.id.button_roll).setVisibility(View.VISIBLE);

    }

    static void updateDice(byte[] scores) {
        byte modifier = 0;
        if (scores[0] == 10) {
            modifier = 1;
        }
        for (int i = modifier; i < (scores.length); i++) {
            Button b = ACTIVITY.findViewById(R.id.dice_1 + i - modifier);
            switch (scores[i]) {
                case Die.ENERGY:
                    b.setBackgroundResource(R.drawable.energy);
                    break;
                case Die.SMASH:
                    b.setBackgroundResource(R.drawable.attack);
                    break;
                case Die.HEAL:
                    b.setBackgroundResource(R.drawable.health);
                    break;
                case Die.ONE:
                    b.setBackgroundResource(R.drawable.one);
                    break;
                case Die.TWO:
                    b.setBackgroundResource(R.drawable.two);
                    break;
                case Die.THREE:
                    b.setBackgroundResource(R.drawable.three);
                    break;
                default:
                    break;
            }
        }
    }

}
