package com.lagradost.fastani

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.widget.SeekBar
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.C
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.media.widget.CastSeekBar
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.lagradost.fastani.MainActivity.Companion.activity
import com.lagradost.fastani.MainActivity.Companion.getColorFromAttr

class ControllerActivity : ExpandedControllerActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.cast_expanded_controller_menu, menu);
        CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item)
        val bar = findViewById<CastSeekBar>(R.id.cast_seek_bar)
        println(bar)
        val colorCodeDark = Color.parseColor("#0F52BA");
        //bar.
        /* (
            this.getColorFromAttr(R.attr.colorPrimary)
        )*/
        return true
    }
}