package eu.zipf.zeno.kingo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ToggleButton;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.Game;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;

import eu.zipf.zeno.kingo.bc.R;

public class MainActivity extends Activity implements View.OnClickListener {

    public Game game;
    public PlayAPI playAPI;
    public static final String TAG = "BabyDragon";
    ArrayList<LocalPlayer> players;
    ArrayList<ToggleButton> dices;
    static Dice dc;
    int participantCnt;
    public static byte DICE_RESULTS = 10;


    //UI Element implement

    private int rollCnt = 0;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pass Activity to other classes
        passActivity();


        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);


        // Create the client used to sign in.
        playAPI = new PlayAPI();
        playAPI.setGoogleSignInClient();

        // set up a click listener for everything we care about
        for (int id : UpdateUI.getClickables()) {
            findViewById(id).setOnClickListener(this);
        }

        UpdateUI.switchToMainScreen(playAPI);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");

        // Since the state of the signed in user can change when the activity is not active
        // it is recommended to try and sign in silently from when the app resumes.
        playAPI.signInSilently();
    }

    @Override
    protected void onPause() {
        super.onPause();
        playAPI.onPause();

    }
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_sign_in:
                // start the sign-in flow
                Log.d(TAG, "Sign-in button clicked");
                playAPI.startSignInIntent();
                break;
            case R.id.button_sign_out:
                // user wants to sign out
                // sign out.
                Log.d(TAG, "Sign-out button clicked");
                playAPI.signOut();
                UpdateUI.switchToScreen(R.id.screen_sign_in, playAPI);
                break;
            case R.id.button_invite_players:
                playAPI.onButtonInvitePlayers();
                break;
            case R.id.button_see_invitations:
                playAPI.onButtonSeeInvitations();
                break;
            case R.id.button_accept_popup_invitation:
                playAPI.onButtonAcceptPopupInvitations();
                break;
            case R.id.button_quick_game:
                // user wants to play against a random opponent right now
                playAPI.startQuickGame();
                break;
            case R.id.button_roll:
                if (rollCnt % 4 == 0) {
                    rollDice();
                } else {
                    rerollDices(Multiplayer.getReroll());
                }
                rollCnt++;
                broadcastDice();
                UpdateUI.updateDice(dc.getScores());
                if (rollCnt % 4 == 3) {
                    rollCnt++;  //TODO: ?????
                    nextPlayer();
                }
                break;
            case R.id.button_next:
                setupWait();
                eu.zipf.zeno.kingo.Multiplayer.broadcastReset(playAPI.getRoomId());
                eu.zipf.zeno.kingo.Multiplayer.broadcast((byte) 11, playAPI.getRoomId());

                //Hier kommen dann eventuell noch andere Buttons hinzu.
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {

        if (requestCode == playAPI.getRC("RC_SIGN_IN")) {

            Task<GoogleSignInAccount> task =
                    GoogleSignIn.getSignedInAccountFromIntent(intent);

            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                playAPI.onConnected(account);
            } catch (ApiException apiException) {
                String message = apiException.getMessage();
                if (message == null || message.isEmpty()) {
                    message = getString(R.string.signin_other_error);
                }

                playAPI.onDisconnected();

                new AlertDialog.Builder(this)
                        .setMessage(message)
                        .setNeutralButton(android.R.string.ok, null)
                        .show();
            }
        } else if (requestCode == playAPI.getRC("RC_SELECT_PLAYERS")) {
            // we got the result from the "select localPlayers" UI -- ready to create the room
            playAPI.handleSelectPlayersResult(resultCode, intent);

        } else if (requestCode == playAPI.getRC("RC_INVITATION_INBOX")) {
            // we got the result from the "select invitation" UI (invitation inbox). We're
            // ready to accept the selected invitation:
            playAPI.handleInvitationInboxResult(resultCode, intent);

        } else if (requestCode == playAPI.getRC("RC_WAITING_ROOM")) {
            // we got the result from the "waiting room" UI.
            if (resultCode == Activity.RESULT_OK) {
                // ready to start playing
                Log.d(TAG, "Starting game (waiting room returned OK).");
                startGame();
            } else if (resultCode == GamesActivityResultCodes.RESULT_LEFT_ROOM) {
                // player indicated that they want to leave the room
                playAPI.leaveRoom();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // Dialog was cancelled (user pressed back key, for instance). In our game,
                // this means leaving the room too. In more elaborate games, this could mean
                // something else (like minimizing the waiting room UI).
                playAPI.leaveRoom();
            }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    // Activity is going to the background. We have to leave the current room.
    @Override
    public void onStop() {
        Log.d(TAG, "**** got onStop");
        setContentView(R.layout.layout_game);

        // if we're in a room, leave it.
        playAPI.leaveRoom();

        // stop trying to keep the screen on
        playAPI.stopKeepingScreenOn();

        UpdateUI.switchToMainScreen(playAPI);

        super.onStop();
    }

    // Handle back key to make sure we cleanly leave a game if we are in the middle of one
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK && UpdateUI.mCurScreen == R.id.screen_game) {
            playAPI.leaveRoom();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }





    /*
     * GAME LOGIC SECTION. Methods that implement the game's rules.
     */

    // Current state of the game:


    int mScore = 0; // user's current score

    // Reset game variables in preparation for a new game.
    static void resetGameVars() {

    }

    // Start the gameplay phase of the game.
    void startGame() {
        UpdateUI.updateScoreDisplay();
        //eu.zipf.zeno.kingo.Multiplayer.broadcastScore(10, 0, 0);


        //Game.main(null);
        initialize();
        Log.d(TAG, playAPI.getParticipants().get(0).getParticipantId() + "        " + playAPI.getParticipants().size() + "      " + playAPI.getMyId());
        if (playAPI.getParticipants().get(0).getParticipantId().equals(playAPI.getMyId())) {
            setupStart();
        } else {
            setupWait();
        }


        // run the gameTick() method every second to update the game.
        final Handler h = new Handler();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {

                gameTick();
                h.postDelayed(this, 1000);
            }
        }, 1000);
    }


    // Game tick -- update countdown, check if game ended.
    void gameTick() {

    }

    // indicates the player scored one point
    void scoreOnePoint() {

        ++mScore;
        UpdateUI.updateScoreDisplay();
        UpdateUI.updatePeerScoresDisplay();

        // broadcast our new score to our peers
        //broadcastScore();
    }

    public void setupStart() {
        setContentView(R.layout.layout_game);
        findViewById(R.id.button_roll).setOnClickListener(this);
    }

    public void setupWait() {
        setContentView(R.layout.layout_game);
        findViewById(R.id.button_roll).setClickable(false);
        findViewById(R.id.button_roll).setVisibility(View.GONE);
        findViewById(R.id.button_next).setVisibility(View.GONE);
        //findViewById(R.id.button_roll).setOnClickListener(this);
    }




    private void initialize() {
        players = new ArrayList<>();
        dc = new Dice(6);
        this.participantCnt = playAPI.getParticipants().size();
        fillPlayers();
    }

    private void fillPlayers() {

        int i = 0;
        for (Participant p : playAPI.getParticipants()) {

            players.add(new LocalPlayer(p.getDisplayName(), i));
            i++;
        }
    }

    public static void rollDice() {
        dc.roll();
        Log.d(MainActivity.TAG, "Buttong R0LL");
    }
    public static void rerollDices(byte[] index){
        dc.reroll(index);
    }


    public void broadcastDice() {
        ArrayList<Byte> toBrod = new ArrayList<Byte>();
        toBrod.add(DICE_RESULTS);
        byte[] scores = dc.getScores();
        for(byte b:scores){
            toBrod.add(b);
        }
        Multiplayer.broadcast(toBrod, playAPI.getRoomId());
    }

    public static Dice getDice(){
        return dc;
    }
    public void nextPlayer() {
        findViewById(R.id.button_next).setVisibility(View.VISIBLE);
        findViewById(R.id.button_next).setOnClickListener(this);
        findViewById(R.id.button_roll).setEnabled(false);
        //game.resolveDice();
    }

    public void myTurn() {
        UpdateUI.forMyTurn();
        findViewById(R.id.button_roll).setOnClickListener(this);
    }



    /*
     * MISC SECTION. Miscellaneous methods.
     */


    void passActivity() {
        UpdateUI.setActivity(this);
        eu.zipf.zeno.kingo.Multiplayer.setActivity(this);
        PlayAPI.setActivity(this);


    }









}
