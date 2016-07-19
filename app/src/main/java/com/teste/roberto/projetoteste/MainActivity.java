package com.teste.roberto.projetoteste;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener, View.OnClickListener {

    private static final String TAG = "GoogleActivity";
    private static final int RC_SIGN_IN = 9001;
    private TextView mStatusTextView;
    private TextView mDetailTextView;

    Handler handler = new Handler();

    SharedPreferences sharedpreferences;

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;

    private GoogleApiClient mGoogleApiClient;
    private GoogleSignInAccount account;

    private ImageView imgView;
    private TextView mMyNameTextView;



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);
        findViewById(R.id.revoke_button).setOnClickListener(this);
        mMyNameTextView = (TextView) findViewById(R.id.title_text);
        mStatusTextView = (TextView) findViewById(R.id.status);
        mDetailTextView = (TextView) findViewById(R.id.detail);
        imgView = (ImageView)findViewById(R.id.google_icon);


        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();


        mGoogleApiClient = new GoogleApiClient.Builder(this).enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */).addApi(Auth.GOOGLE_SIGN_IN_API, gso).build();
        sharedpreferences = getSharedPreferences("idToken", Context.MODE_PRIVATE);
        mAuth = FirebaseAuth.getInstance();

        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                } else {
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                }
                updateUI(user);
            }
        };

    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
        if (sharedpreferences.getString("idToken", null) != null) firebaseAuthWithGoogle();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
        finish();

    }

    // [START onactivityresult]
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                account = result.getSignInAccount();
                sharedpreferences.edit().clear().putString("idToken", account.getIdToken()).commit();
                firebaseAuthWithGoogle();
            } else {
                updateUI(null);
                findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            }


        }
    }

    private void firebaseAuthWithGoogle() {
        String idToken = sharedpreferences.getString("idToken", null);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());
                if (!task.isSuccessful()) {
                    Log.w(TAG, "signInWithCredential", task.getException());
                    Toast.makeText(MainActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void signIn() {
        findViewById(R.id.sign_in_button).setVisibility(View.GONE);
        if (sharedpreferences.getString("idToken", null) == null) {
            Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
            startActivityForResult(signInIntent, RC_SIGN_IN);
        }
        else firebaseAuthWithGoogle();
    }


    private void signOut() {
        mAuth.signOut();
        // Google sign out
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                updateUI(null);
            }
        });
    }

    private void revokeAccess() {
        // Firebase sign out
        mAuth.signOut();

        // Google revoke access
        Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                updateUI(null);
            }
        });
        sharedpreferences.edit().clear().commit();
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;
            case R.id.sign_out_button:
                signOut();
                break;
            case R.id.revoke_button:
                revokeAccess();
                break;
        }
    }


    private void updateUI(FirebaseUser user) {

        if (user != null) {

            mMyNameTextView.setText(user.getDisplayName());
            mStatusTextView.setText(user.getEmail());
            mDetailTextView.setText(user.getUid());
            //imgView.setImageURI(user.getPhotoUrl());
            getImagem(imgView, user.getPhotoUrl());
            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            findViewById(R.id.disconnect_button).setVisibility(View.VISIBLE);

        } else {
            mMyNameTextView.setText(null);
            mStatusTextView.setText(null);
            mDetailTextView.setText(null);
            imgView.setImageResource(0);
            findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            findViewById(R.id.disconnect_button).setVisibility(View.GONE);


        }
    }
    public void getImagem(final ImageView imgv, final Uri urlimg) {

        new Thread() {
            public void run() {
                Bitmap img = null;
                try {
                    URL url = new URL(urlimg.toString());
                    HttpURLConnection conec = (HttpURLConnection) url.openConnection();
                    InputStream is = conec.getInputStream();
                    img = BitmapFactory.decodeStream(is);
                } catch (IOException e) {
                    Log.d("GET IMAGE", urlimg.toString());
                    Log.d("GET IMAGE", e.getMessage());
                }
                final Bitmap imgaux = img;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        imgv.setImageBitmap(imgaux);
                    }
                });
            }
        }.start();
    }
}




