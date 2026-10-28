package org.peyilo.readview.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.peyilo.libreadview.AbstractPageContainer
import org.peyilo.libreadview.turning.GoogleCurlEffect
import org.peyilo.libreadview.turning.CoverEffect
import org.peyilo.libreadview.turning.IosCurlEffect
import org.peyilo.libreadview.turning.IBookSlideEffect
import org.peyilo.libreadview.turning.ScrollEffect
import org.peyilo.libreadview.turning.SlideEffect
import org.peyilo.readview.R

class PageTurnChangeFragment(
    private val pageContainer: AbstractPageContainer
): BaseBottomFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_pageturn_change, container, false)
        initView(view)
        return view
    }

    private fun initView(view: View) {
        view.findViewById<View>(R.id.pageturn_cover).setOnClickListener {
            if (pageContainer.pageEffect is CoverEffect) return@setOnClickListener
            pageContainer.pageEffect = CoverEffect()
        }
        view.findViewById<View>(R.id.pageturn_slide).setOnClickListener {
            if (pageContainer.pageEffect is SlideEffect) return@setOnClickListener
            pageContainer.pageEffect = SlideEffect()
        }
        view.findViewById<View>(R.id.pageturn_simulation).setOnClickListener {
            if (pageContainer.pageEffect is IosCurlEffect) return@setOnClickListener
            pageContainer.pageEffect = IosCurlEffect()
        }
        view.findViewById<View>(R.id.pageturn_google_curl).setOnClickListener {
            if (pageContainer.pageEffect is GoogleCurlEffect) return@setOnClickListener
            pageContainer.pageEffect = GoogleCurlEffect()
        }
        view.findViewById<View>(R.id.pageturn_scroll).setOnClickListener {
            if (pageContainer.pageEffect is ScrollEffect) return@setOnClickListener
            pageContainer.pageEffect = ScrollEffect()
        }
        view.findViewById<View>(R.id.pageturn_ibook_slide).setOnClickListener {
            if (pageContainer.pageEffect is IBookSlideEffect) return@setOnClickListener
            pageContainer.pageEffect = IBookSlideEffect()
        }

    }

}