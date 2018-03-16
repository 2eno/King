package eu.zipf.zeno.kingo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesCallbackStatusCodes;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.GamesClientStatusCodes;
import com.google.android.gms.games.InvitationsClient;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.games.RealTimeMultiplayerClient;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.InvitationCallback;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateCallback;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

import eu.zipf.zeno.kingo.bc.R;

/**
 * Created by zeno on 16.03.18.
 */

public class PlayAPI {

    private static Activity ACTIVITY = new Activity();

    static void setActivity(Activity activity) {
        ACTIVITY = activity;
    }
    // Request codes for the UIs that we show with startActivityForResult:
    final static int RC_SELECT_PLAYERS = 10000;
    final static int RC_INVITATION_INBOX = 10001;
    final static int RC_WAITING_ROOM = 10002;

    // Request code used to invoke sign in user interactions.
    private static final int RC_SIGN_IN = 9001;
    // Client used to sign in with Google APIs
    private GoogleSignInClient mGoogleSignInClient = null;

    // Client used to interact with the real time multiplayer system.
    static RealTimeMultiplayerClient mRealTimeMultiplayerClient = null;

    // Client used to interact with the Invitation system.
    private InvitationsClient mInvitationsClient = null;

    // Room ID where the currently active game is taking place; null if we're
    // not playing.
    public static String mRoomId = null;

    // Holds the configuration of the current room.
    RoomConfig mRoomConfig;

    // Are we playing in multiplayer mode?
    //boolean mMultiplayer = false;

    // The participants in the currently active game
    static ArrayList<Participant> mParticipants = null;

    // My participant ID in the currently active game
    static String mMyId = null;


    // If non-null, this is the id of the invitation we received via the
    // invitation listener
    public static String mIncomingInvitationId = null;


    GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN).requestEmail().build();

    void startQuickGame() {
        // quick-start a game with one randomly selected opponent
        final int MIN_OPPONENTS = 1, MAX_OPPONENTS = 2;
        Bundle autoMatchCriteria = RoomConfig.createAutoMatchCriteria(MIN_OPPONENTS,
                MAX_OPPONENTS, 0);
        UpdateUI.switchToScreen(R.id.screen_wait, this);
        keepScreenOn();
        MainActivity.resetGameVars();

        mRoomConfig = RoomConfig.builder(mRoomUpdateCallback)
                .setOnMessageReceivedListener(Multiplayer.mOnRealTimeMessageReceivedListener)
                .setRoomStatusUpdateCallback(mRoomStatusUpdateCallback)
                .setAutoMatchCriteria(autoMatchCriteria)
                .build();
        mRealTimeMultiplayerClient.create(mRoomConfig);
    }
    /**
     * Start a sign in activity.  To properly handle the result, call tryHandleSignInResult from
     * your Activity's onActivityResult function
     */
    public void startSignInIntent() {
        ACTIVITY.startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    /**
     * Try to sign in without displaying dialogs to the user.
     * <p>
     * If the user has already signed in previously, it will not show dialog.
     */
    public void signInSilently() {
        Log.d(MainActivity.TAG, "signInSilently()");

        mGoogleSignInClient.silentSignIn().addOnCompleteListener(ACTIVITY,
                new OnCompleteListener<GoogleSignInAccount>() {
                    @Override
                    public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                        if (task.isSuccessful()) {
                            Log.d(MainActivity.TAG, "signInSilently(): success");
                            onConnected(task.getResult());
                        } else {
                            Log.d(MainActivity.TAG, "signInSilently(): failure", task.getException());
                            onDisconnected();
                        }
                    }
                });
    }

    public void signOut() {
        Log.d(MainActivity.TAG, "signOut()");

        mGoogleSignInClient.signOut().addOnCompleteListener(ACTIVITY,
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {

                        if (task.isSuccessful()) {
                            Log.d(MainActivity.TAG, "signOut(): success");
                        } else {
                            handleException(task.getException(), "signOut() failed!");
                        }

                        onDisconnected();
                    }
                });
    }
    // Handle the result of the "Select localPlayers UI" we launched when the user clicked the
    // "Invite friends" button. We react by creating a room with those localPlayers.

    void handleSelectPlayersResult(int response, Intent data) {
        if (response != Activity.RESULT_OK) {
            Log.w(MainActivity.TAG, "*** select localPlayers UI cancelled, " + response);
            UpdateUI.switchToMainScreen(this);
            return;
        }

        Log.d(MainActivity.TAG, "Select localPlayers UI succeeded.");

        // get the invitee list
        final ArrayList<String> invitees = data.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);
        Log.d(MainActivity.TAG, "Invitee count: " + invitees.size());

        // get the automatch criteria
        Bundle autoMatchCriteria = null;
        int minAutoMatchPlayers = data.getIntExtra(com.google.android.gms.games.multiplayer.Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
        int maxAutoMatchPlayers = data.getIntExtra(com.google.android.gms.games.multiplayer.Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);
        if (minAutoMatchPlayers > 0 || maxAutoMatchPlayers > 0) {
            autoMatchCriteria = RoomConfig.createAutoMatchCriteria(
                    minAutoMatchPlayers, maxAutoMatchPlayers, 0);
            Log.d(MainActivity.TAG, "Automatch criteria: " + autoMatchCriteria);
        }

        // create the room
        Log.d(MainActivity.TAG, "Creating room...");
        UpdateUI.switchToScreen(R.id.screen_wait, this);
        keepScreenOn();
        MainActivity.resetGameVars();

        mRoomConfig = RoomConfig.builder(mRoomUpdateCallback)
                .addPlayersToInvite(invitees)
                .setOnMessageReceivedListener(Multiplayer.mOnRealTimeMessageReceivedListener)
                .setRoomStatusUpdateCallback(mRoomStatusUpdateCallback)
                .setAutoMatchCriteria(autoMatchCriteria).build();
        mRealTimeMultiplayerClient.create(mRoomConfig);
        Log.d(MainActivity.TAG, "Room created, waiting for it to be ready...");
    }

    // Handle the result of the invitation inbox UI, where the player can pick an invitation
    // to accept. We react by accepting the selected invitation, if any.
    void handleInvitationInboxResult(int response, Intent data) {
        if (response != Activity.RESULT_OK) {
            Log.w(MainActivity.TAG, "*** invitation inbox UI cancelled, " + response);
            UpdateUI.switchToMainScreen(this);
            return;
        }

        Log.d(MainActivity.TAG, "Invitation inbox UI succeeded.");
        Invitation invitation = data.getExtras().getParcelable(com.google.android.gms.games.multiplayer.Multiplayer.EXTRA_INVITATION);

        // accept invitation
        if (invitation != null) {
            acceptInviteToRoom(invitation.getInvitationId());
        }
    }

    // Accept the given invitation.
    void acceptInviteToRoom(String invitationId) {
        // accept the invitation
        Log.d(MainActivity.TAG, "Accepting invitation: " + invitationId);

        mRoomConfig = RoomConfig.builder(mRoomUpdateCallback)
                .setInvitationIdToAccept(invitationId)
                .setOnMessageReceivedListener(Multiplayer.mOnRealTimeMessageReceivedListener)
                .setRoomStatusUpdateCallback(mRoomStatusUpdateCallback)
                .build();

        UpdateUI.switchToScreen(R.id.screen_wait,this);
        keepScreenOn();
        MainActivity.resetGameVars();

        mRealTimeMultiplayerClient.join(mRoomConfig)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(MainActivity.TAG, "Room Joined Successfully!");
                    }
                });
    }
    // Leave the room.
    void leaveRoom() {
        Log.d(MainActivity.TAG, "Leaving room.");

        stopKeepingScreenOn();
        if (mRoomId != null) {
            mRealTimeMultiplayerClient.leave(mRoomConfig, mRoomId)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            mRoomId = null;
                            mRoomConfig = null;
                        }
                    });
            UpdateUI.switchToScreen(R.id.screen_wait, this);
        } else {
            UpdateUI.switchToMainScreen(this);
        }
    }

    // Show the waiting room UI to track the progress of other localPlayers as they enter the
    // room and get connected.
    void showWaitingRoom(Room room) {
        // minimum number of localPlayers required for our game
        // For simplicity, we require everyone to join the game before we start it
        // (this is signaled by Integer.MAX_VALUE).
        final int MIN_PLAYERS = Integer.MAX_VALUE;
        mRealTimeMultiplayerClient.getWaitingRoomIntent(room, MIN_PLAYERS)
                .addOnSuccessListener(new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        // show waiting room UI
                        ACTIVITY.startActivityForResult(intent, RC_WAITING_ROOM);
                    }
                })
                .addOnFailureListener(createFailureListener("There was a problem getting the waiting room!"));
    }

    private InvitationCallback mInvitationCallback = new InvitationCallback() {
        // Called when we get an invitation to play a game. We react by showing that to the user.
        @Override
        public void onInvitationReceived(@NonNull Invitation invitation) {
            // We got an invitation to play a game! So, store it in
            // mIncomingInvitationId
            // and show the popup on the screen.

            mIncomingInvitationId = invitation.getInvitationId();
            ((TextView) ACTIVITY.findViewById(R.id.incoming_invitation_text)).setText(
                    invitation.getInviter().getDisplayName() + " " +
                            ACTIVITY.getString(R.string.is_inviting_you));
            UpdateUI.switchToScreen(UpdateUI.mCurScreen,PlayAPI.this); // This will show the invitation popup
        }

        @Override
        public void onInvitationRemoved(@NonNull String invitationId) {

            if (mIncomingInvitationId.equals(invitationId) && mIncomingInvitationId != null) {
                mIncomingInvitationId = null;
                UpdateUI.switchToScreen(UpdateUI.mCurScreen, PlayAPI.this); // This will hide the invitation popup
            }
        }
    };

    /*
     * CALLBACKS SECTION. This section shows how we implement the several games
     * API callbacks.
     */

    private String mPlayerId;

    // The currently signed in account, used to check the account has changed outside of this activity when resuming.
    GoogleSignInAccount mSignedInAccount = null;

    void onConnected(GoogleSignInAccount googleSignInAccount) {
        Log.d(MainActivity.TAG, "onConnected(): connected to Google APIs");
        if (mSignedInAccount != googleSignInAccount) {

            mSignedInAccount = googleSignInAccount;

            // update the clients
            mRealTimeMultiplayerClient = Games.getRealTimeMultiplayerClient(ACTIVITY, googleSignInAccount);
            mInvitationsClient = Games.getInvitationsClient(ACTIVITY, googleSignInAccount);

            // get the playerId from the PlayersClient
            PlayersClient playersClient = Games.getPlayersClient(ACTIVITY, googleSignInAccount);
            playersClient.getCurrentPlayer()
                    .addOnSuccessListener(new OnSuccessListener<Player>() {
                        @Override
                        public void onSuccess(Player player) {
                            mPlayerId = player.getPlayerId();

                            UpdateUI.switchToMainScreen(PlayAPI.this);
                        }
                    })
                    .addOnFailureListener(createFailureListener("There was a problem getting the player id!"));
        }

        // register listener so we are notified if we receive an invitation to play
        // while we are in the game
        mInvitationsClient.registerInvitationCallback(mInvitationCallback);

        // get the invitation from the connection hint
        // Retrieve the TurnBasedMatch from the connectionHint
        GamesClient gamesClient = Games.getGamesClient(ACTIVITY, googleSignInAccount);
        gamesClient.getActivationHint()
                .addOnSuccessListener(new OnSuccessListener<Bundle>() {
                    @Override
                    public void onSuccess(Bundle hint) {
                        if (hint != null) {
                            Invitation invitation =
                                    hint.getParcelable(com.google.android.gms.games.multiplayer.Multiplayer.EXTRA_INVITATION);

                            if (invitation != null && invitation.getInvitationId() != null) {
                                // retrieve and cache the invitation ID
                                Log.d(MainActivity.TAG, "onConnected: connection hint has a room invite!");
                                acceptInviteToRoom(invitation.getInvitationId());
                            }
                        }
                    }
                })
                .addOnFailureListener(createFailureListener("There was a problem getting the activation hint!"));
    }

    private OnFailureListener createFailureListener(final String string) {
        return new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                handleException(e, string);
            }
        };
    }

    void onDisconnected() {
        Log.d(MainActivity.TAG, "onDisconnected()");

        mRealTimeMultiplayerClient = null;
        mInvitationsClient = null;

        UpdateUI.switchToMainScreen(this);
    }

    private RoomStatusUpdateCallback mRoomStatusUpdateCallback = new RoomStatusUpdateCallback() {
        // Called when we are connected to the room. We're not ready to play yet! (maybe not everybody
        // is connected yet).
        @Override
        public void onConnectedToRoom(Room room) {
            Log.d(MainActivity.TAG, "onConnectedToRoom.");

            //get participants and my ID:
            mParticipants = room.getParticipants();
            mMyId = room.getParticipantId(mPlayerId);

            // save room ID if its not initialized in onRoomCreated() so we can leave cleanly before the game starts.
            if (mRoomId == null) {
                mRoomId = room.getRoomId();
            }

            // print out the list of participants (for debug purposes)
            Log.d(MainActivity.TAG, "Room ID: " + mRoomId);
            Log.d(MainActivity.TAG, "My ID " + mMyId);
            Log.d(MainActivity.TAG, "<< CONNECTED TO ROOM>>");
        }

        // Called when we get disconnected from the room. We return to the main screen.
        @Override
        public void onDisconnectedFromRoom(Room room) {
            mRoomId = null;
            mRoomConfig = null;
            showGameError();
        }


        // We treat most of the room update callbacks in the same way: we update our list of
        // participants and update the display. In a real game we would also have to check if that
        // change requires some action like removing the corresponding player avatar from the screen,
        // etc.
        @Override
        public void onPeerDeclined(Room room, @NonNull List<String> arg1) {
            updateRoom(room);
        }

        @Override
        public void onPeerInvitedToRoom(Room room, @NonNull List<String> arg1) {
            updateRoom(room);
        }

        @Override
        public void onP2PDisconnected(@NonNull String participant) {
        }

        @Override
        public void onP2PConnected(@NonNull String participant) {
        }

        @Override
        public void onPeerJoined(Room room, @NonNull List<String> arg1) {
            updateRoom(room);
        }

        @Override
        public void onPeerLeft(Room room, @NonNull List<String> peersWhoLeft) {
            updateRoom(room);
        }

        @Override
        public void onRoomAutoMatching(Room room) {
            updateRoom(room);
        }

        @Override
        public void onRoomConnecting(Room room) {
            updateRoom(room);
        }

        @Override
        public void onPeersConnected(Room room, @NonNull List<String> peers) {
            updateRoom(room);
        }

        @Override
        public void onPeersDisconnected(Room room, @NonNull List<String> peers) {
            updateRoom(room);
        }
    };

    // Show error message about game being cancelled and return to main screen.
    void showGameError() {
        new AlertDialog.Builder(ACTIVITY)
                .setMessage(ACTIVITY.getString(R.string.game_problem))
                .setNeutralButton(android.R.string.ok, null).create();

        UpdateUI.switchToMainScreen(this);
    }

    private RoomUpdateCallback mRoomUpdateCallback = new RoomUpdateCallback() {

        // Called when room has been created
        @Override
        public void onRoomCreated(int statusCode, Room room) {
            Log.d(MainActivity.TAG, "onRoomCreated(" + statusCode + ", " + room + ")");
            if (statusCode != GamesCallbackStatusCodes.OK) {
                Log.e(MainActivity.TAG, "*** Error: onRoomCreated, status " + statusCode);
                showGameError();
                return;
            }

            // save room ID so we can leave cleanly before the game starts.
            mRoomId = room.getRoomId();

            // show the waiting room UI
            showWaitingRoom(room);
        }

        // Called when room is fully connected.
        @Override
        public void onRoomConnected(int statusCode, Room room) {
            Log.d(MainActivity.TAG, "onRoomConnected(" + statusCode + ", " + room + ")");
            if (statusCode != GamesCallbackStatusCodes.OK) {
                Log.e(MainActivity.TAG, "*** Error: onRoomConnected, status " + statusCode);
                showGameError();
                return;
            }
            updateRoom(room);
        }

        @Override
        public void onJoinedRoom(int statusCode, Room room) {
            Log.d(MainActivity.TAG, "onJoinedRoom(" + statusCode + ", " + room + ")");
            if (statusCode != GamesCallbackStatusCodes.OK) {
                Log.e(MainActivity.TAG, "*** Error: onRoomConnected, status " + statusCode);
                showGameError();
                return;
            }

            // show the waiting room UI
            showWaitingRoom(room);
        }

        // Called when we've successfully left the room (this happens a result of voluntarily leaving
        // via a call to leaveRoom(). If we get disconnected, we get onDisconnectedFromRoom()).
        @Override
        public void onLeftRoom(int statusCode, @NonNull String roomId) {
            // we have left the room; return to main screen.
            Log.d(MainActivity.TAG, "onLeftRoom, code " + statusCode);
            UpdateUI.switchToMainScreen(PlayAPI.this);
        }
    };

    void updateRoom(Room room) {
        if (room != null) {
            mParticipants = room.getParticipants();
        }
        if (mParticipants != null) {
            UpdateUI.updatePeerScoresDisplay();
        }
    }


    /**
     * Since a lot of the operations use tasks, we can use a common handler for whenever one fails.
     *
     * @param exception The exception to evaluate.  Will try to display a more descriptive reason for the exception.
     * @param details   Will display alongside the exception if you wish to provide more details for why the exception
     *                  happened
     */
    private void handleException(Exception exception, String details) {
        int status = 0;

        if (exception instanceof ApiException) {
            ApiException apiException = (ApiException) exception;
            status = apiException.getStatusCode();
        }

        String errorString = null;
        switch (status) {
            case GamesCallbackStatusCodes.OK:
                break;
            case GamesClientStatusCodes.MULTIPLAYER_ERROR_NOT_TRUSTED_TESTER:
                errorString = ACTIVITY.getString(R.string.status_multiplayer_error_not_trusted_tester);
                break;
            case GamesClientStatusCodes.MATCH_ERROR_ALREADY_REMATCHED:
                errorString = ACTIVITY.getString(R.string.match_error_already_rematched);
                break;
            case GamesClientStatusCodes.NETWORK_ERROR_OPERATION_FAILED:
                errorString = ACTIVITY.getString(R.string.network_error_operation_failed);
                break;
            case GamesClientStatusCodes.INTERNAL_ERROR:
                errorString = ACTIVITY.getString(R.string.internal_error);
                break;
            case GamesClientStatusCodes.MATCH_ERROR_INACTIVE_MATCH:
                errorString = ACTIVITY.getString(R.string.match_error_inactive_match);
                break;
            case GamesClientStatusCodes.MATCH_ERROR_LOCALLY_MODIFIED:
                errorString = ACTIVITY.getString(R.string.match_error_locally_modified);
                break;
            default:
                errorString = ACTIVITY.getString(R.string.unexpected_status, GamesClientStatusCodes.getStatusCodeString(status));
                break;
        }

        if (errorString == null) {
            return;
        }

        String message = ACTIVITY.getString(R.string.status_exception_error, details, status, exception);

        new AlertDialog.Builder(ACTIVITY)
                .setTitle("Error")
                .setMessage(message + "\n" + errorString)
                .setNeutralButton(android.R.string.ok, null)
                .show();
    }
    // Sets the flag to keep this screen on. It's recommended to do that during
    // the
    // handshake when setting up a game, because if the screen turns off, the
    // game will be
    // cancelled.
    void keepScreenOn() {
        ACTIVITY.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    // Clears the flag that keeps the screen on.
    void stopKeepingScreenOn() {
        ACTIVITY.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    void setGoogleSignInClient () {

        mGoogleSignInClient = GoogleSignIn.getClient(ACTIVITY, GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
    }
    void onPause() {
        // unregister our listeners.  They will be re-registered via onResume->signInSilently->onConnected.
        if (mInvitationsClient != null) {
            mInvitationsClient.unregisterInvitationCallback(mInvitationCallback);
        }
    }
    void onButtonInvitePlayers() {
        UpdateUI.switchToScreen(R.id.screen_wait, this);
        Log.d(MainActivity.TAG, "Sign-out button clicked");
        // show list of invitable localPlayers
        mRealTimeMultiplayerClient.getSelectOpponentsIntent(1, 3).addOnSuccessListener(
                new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        ACTIVITY.startActivityForResult(intent, RC_SELECT_PLAYERS);
                    }
                }
        ).addOnFailureListener(createFailureListener("There was a problem selecting opponents."));
    }
    void onButtonSeeInvitations() {
        UpdateUI.switchToScreen(R.id.screen_wait, this);

        // show list of pending invitations
        mInvitationsClient.getInvitationInboxIntent().addOnSuccessListener(
                new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        ACTIVITY.startActivityForResult(intent, RC_INVITATION_INBOX);
                    }
                }
        ).addOnFailureListener(createFailureListener("There was a problem getting the inbox."));
    }
    void onButtonAcceptPopupInvitations() {
        // user wants to accept the invitation shown on the invitation popup
        // (the one we got through the OnInvitationReceivedListener).
        acceptInviteToRoom(mIncomingInvitationId);
        mIncomingInvitationId = null;
    }
    int getRC(String _RC) {
        int RC = 0;
        switch(_RC) {
            case "RC_SIGN_IN":
                RC = RC_SIGN_IN;
                break;
            case "RC_SELECT_PLAYERS":
                RC = RC_SELECT_PLAYERS;
                break;
            case "RC_INVITATION_INBOX":
                RC = RC_INVITATION_INBOX;
                break;
            case "RC_WAITING_ROOM":
                RC = RC_WAITING_ROOM;
                break;
        }
        return RC;
    }
    ArrayList<Participant> getParticipants() {
        return mParticipants;
    }
    String getMyId() {
        return mMyId;
    }
    String getRoomId() {
        return mRoomId;
    }
}
