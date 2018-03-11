package eu.zipf.zeno.games.bc;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

/**
 * Created by zeno on 11.03.18.
 */

public class UpdateUI {

    static Activity activity = new Activity();
    public static void setActivity(Activity _activity){
        activity = _activity;
    }

    /*
     * UI SECTION. Methods that implement the game's UI.
     */

    // This array lists everything that's clickable, so we can install click
    // event handlers.
    final static int[] CLICKABLES = {
            R.id.button_accept_popup_invitation, R.id.button_invite_players,
            R.id.button_quick_game, R.id.button_see_invitations, R.id.button_sign_in
    };

    // This array lists all the individual screens our game has.
    final static int[] SCREENS = {
            R.id.screen_game, R.id.screen_main, R.id.screen_sign_in,
            R.id.screen_wait
    };
    public static int mCurScreen = -1;

    public static int[] getClickables() {
           return CLICKABLES;
    }

    static void switchToScreen(int screenId) {
        // make the requested screen visible; hide all others.
        for (int id : SCREENS) {
            activity.findViewById(id).setVisibility(screenId == id ? View.VISIBLE : View.GONE);
        }
        mCurScreen = screenId;

        // should we show the invitation popup?
        boolean showInvPopup;
        if (MainActivity.mIncomingInvitationId == null) {
            // no invitation, so no popup
            showInvPopup = false;

        } else {
            // single-player: show on main screen and gameplay screen
            showInvPopup = (mCurScreen == R.id.screen_main);
        }
        activity.findViewById(R.id.invitation_popup).setVisibility(showInvPopup ? View.VISIBLE : View.GONE);
    }

    static void switchToMainScreen() {
        if (MainActivity.mRealTimeMultiplayerClient != null) {
            switchToScreen(R.id.screen_main);
        } else {
            switchToScreen(R.id.screen_sign_in);
        }
    }

    // updates the label that shows my score
    static void updateScoreDisplay() {
        ((TextView) activity.findViewById(R.id.my_score)).setText("Test");
    }

    // formats a score as a three-digit number
    String formatScore(int i) {
        if (i < 0) {
            i = 0;
        }
        String s = String.valueOf(i);
        return s.length() == 1 ? "00" + s : s.length() == 2 ? "0" + s : s;
    }

    // updates the screen with the scores from our peers
    static void updatePeerScoresDisplay() {

    }

}
