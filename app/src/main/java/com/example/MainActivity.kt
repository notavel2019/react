package com.example

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.AccessControlRepository
import com.example.firebase.GoogleAuthHelper
import com.example.ui.ReactStudioScreen
import com.example.ui.components.AuthGateScreen
import com.example.ui.theme.ReactStudioTheme
import com.example.viewmodel.ReactStudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            ReactStudioTheme {
                ReactStudioAppRoot()
            }
        }
    }
}

@Composable
private fun ReactStudioAppRoot() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val scope = rememberCoroutineScope()

    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthGateScreen()
    } else {
        // Scope entire Studio screen & ViewModel strictly to user.uid so switching accounts
        // never leaks listeners or admin state between Google accounts.
        key(user.uid) {
            val accessControlRepo = remember(application) {
                AccessControlRepository(application)
            }
            val studioViewModel: ReactStudioViewModel = viewModel(
                key = user.uid,
                factory = ReactStudioViewModel.Factory(
                    application = application,
                    userId = user.uid,
                    userEmail = user.email.orEmpty(),
                    accessControlRepo = accessControlRepo
                )
            )
            ReactStudioScreen(
                viewModel = studioViewModel,
                onSignOut = {
                    scope.launch {
                        GoogleAuthHelper.signOut(context)
                    }
                }
            )
        }
    }
}
