package org.peyilo.readview.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.peyilo.libreadview.manager.CoverLayoutManager
import org.peyilo.libreadview.manager.IBookCurlLayoutManager
import org.peyilo.libreadview.manager.IBookSlideLayoutManager
import org.peyilo.libreadview.manager.ScrollLayoutManager
import org.peyilo.libreadview.manager.SlideLayoutManager
import org.peyilo.libreadview.simple.SimpleReadView
import org.peyilo.readview.R
import org.peyilo.readview.demo.setReadViewTheme

class SettingsFragment(
    private val readview: SimpleReadView
): BaseBottomFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        initView(view)
        return view
    }

    private fun initView(view: View) {
        view.findViewById<View>(R.id.pageturn_cover).setOnClickListener {
            if (readview.layoutManager is CoverLayoutManager) return@setOnClickListener
            readview.layoutManager = CoverLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_slide).setOnClickListener {
            if (readview.layoutManager is SlideLayoutManager) return@setOnClickListener
            readview.layoutManager = SlideLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_simulation).setOnClickListener {
            if (readview.layoutManager is IBookCurlLayoutManager) return@setOnClickListener
            readview.layoutManager = IBookCurlLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_scroll).setOnClickListener {
            if (readview.layoutManager is ScrollLayoutManager) return@setOnClickListener
            readview.layoutManager = ScrollLayoutManager()
        }
        view.findViewById<View>(R.id.pageturn_ibook_slide).setOnClickListener {
            if (readview.layoutManager is IBookSlideLayoutManager) return@setOnClickListener
            readview.layoutManager = IBookSlideLayoutManager()
        }

        view.findViewById<View>(R.id.theme_1).setOnClickListener {
            readview.setReadViewTheme(0)
        }
        view.findViewById<View>(R.id.theme_2).setOnClickListener {
            readview.setReadViewTheme(1)
        }
        view.findViewById<View>(R.id.theme_3).setOnClickListener {
            readview.setReadViewTheme(2)
        }
        view.findViewById<View>(R.id.theme_4).setOnClickListener {
            readview.setReadViewTheme(3)
        }
        view.findViewById<View>(R.id.theme_5).setOnClickListener {
            readview.setReadViewTheme(4)
        }
    }

}