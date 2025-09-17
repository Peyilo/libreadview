package org.peyilo.readview.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.peyilo.libreadview.turning.CoverEffect
import org.peyilo.libreadview.turning.IBookCurlEffect
import org.peyilo.libreadview.turning.IBookSlideEffect
import org.peyilo.libreadview.turning.ScrollEffect
import org.peyilo.libreadview.turning.SlideEffect
import org.peyilo.libreadview.basic.BasicReadView
import org.peyilo.readview.R
import org.peyilo.readview.demo.setReadViewTheme

class SettingsFragment(
    private val readview: BasicReadView
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
            if (readview.pageEffect is CoverEffect) return@setOnClickListener
            readview.pageEffect = CoverEffect()
        }
        view.findViewById<View>(R.id.pageturn_slide).setOnClickListener {
            if (readview.pageEffect is SlideEffect) return@setOnClickListener
            readview.pageEffect = SlideEffect()
        }
        view.findViewById<View>(R.id.pageturn_simulation).setOnClickListener {
            if (readview.pageEffect is IBookCurlEffect) return@setOnClickListener
            readview.pageEffect = IBookCurlEffect()
        }
        view.findViewById<View>(R.id.pageturn_scroll).setOnClickListener {
            if (readview.pageEffect is ScrollEffect) return@setOnClickListener
            readview.pageEffect = ScrollEffect()
        }
        view.findViewById<View>(R.id.pageturn_ibook_slide).setOnClickListener {
            if (readview.pageEffect is IBookSlideEffect) return@setOnClickListener
            readview.pageEffect = IBookSlideEffect()
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