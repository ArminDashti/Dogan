package com.dogan

import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Edge-to-edge (targetSdk 35+) with padding so content clears status and nav bars. */
fun AppCompatActivity.applyEdgeToEdgeInsets() {
    enableEdgeToEdge()
    val root = (findViewById<View>(android.R.id.content) as ViewGroup).getChildAt(0) ?: return
    val baseLeft = root.paddingLeft
    val baseTop = root.paddingTop
    val baseRight = root.paddingRight
    val baseBottom = root.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
        val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            left = baseLeft + bars.left,
            top = baseTop + bars.top,
            right = baseRight + bars.right,
            bottom = baseBottom + bars.bottom,
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(root)
}