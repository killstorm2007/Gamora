package com.gamora.feature.home

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.gamora.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_home)

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

        bottomNavigation.setupWithNavController(navController)

        val fabChatbot =
            findViewById<FloatingActionButton>(R.id.fabChatbot)

        fabChatbot.setOnClickListener {
            if (navController.currentDestination?.id == R.id.chatbotFragment) {
                navController.popBackStack()
            } else {
                navController.navigate(R.id.chatbotFragment)
            }
        }

        navController.addOnDestinationChangedListener {
                _, destination, _ ->

            if (destination.id == R.id.chatbotFragment) {
                fabChatbot.setImageResource(R.drawable.ic_close)
            } else {
                fabChatbot.setImageResource(R.drawable.ic_chat)
            }
        }
    }
}