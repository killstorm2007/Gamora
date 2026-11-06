
package com.gamora.feature.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.gamora.R
import com.gamora.data.SupabaseConfig
import com.gamora.data.repository.AuthRepository
import com.gamora.feature.auth.login.LoginScreen
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_home)

        verificarSesion()

        val root = findViewById<View>(R.id.root_container)

        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHost.navController

        val bottomNavigation =
            findViewById<BottomNavigationView>(R.id.bottom_navigation)

        val fabChatbot =
            findViewById<FloatingActionButton>(R.id.fabChatbot)

        bottomNavigation.setupWithNavController(navController)

        fabChatbot.setOnClickListener {
            if (navController.currentDestination?.id == R.id.chatbotFragment) {
                navController.popBackStack()
            } else {
                navController.navigate(R.id.chatbotFragment)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->


            val isChatbot = destination.id == R.id.chatbotFragment

            if (isChatbot) {
                bottomNavigation.visibility = View.GONE
                fabChatbot.visibility = View.GONE
            } else {
                bottomNavigation.visibility = View.VISIBLE
                fabChatbot.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Si no hay una sesión válida de Supabase, regresa al inicio de sesión.
     */
    private fun verificarSesion() {
        if (!SupabaseConfig.isConfigured) return

        lifecycleScope.launch {
            AuthRepository.awaitSession()

            if (AuthRepository.currentUserId() == null) {
                startActivity(
                    Intent(this@HomeActivity, LoginScreen::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )

                finish()
            }
        }
    }
}
