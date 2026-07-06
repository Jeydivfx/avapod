package com.avapod.app.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avapod.app.R
import com.avapod.app.adapters.CategoryAdapter
import com.avapod.app.models.Category

class ExploreFragment : Fragment() {

    private lateinit var rvCategories: RecyclerView
    private lateinit var loader: ProgressBar

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_explore, container, false)

        rvCategories = view.findViewById(R.id.rv_categories)
        loader = view.findViewById(R.id.loader)

        setupToolbar(view)
        fetchCategories()

        return view
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<View>(R.id.common_toolbar)
        if (toolbar != null) {
            val btnBack = toolbar.findViewById<ImageButton>(R.id.btn_back_common)
            val btnMenu = toolbar.findViewById<ImageButton>(R.id.btn_menu_common)

            btnBack.visibility = View.VISIBLE
            btnBack.scaleX = -1f
            btnMenu.visibility = View.GONE

            btnBack.setOnClickListener {
                if (parentFragmentManager.backStackEntryCount > 0) {
                    parentFragmentManager.popBackStack()
                } else {
                    val bottomNav = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
                    if (bottomNav != null) {
                        // دادن مهلت ۵۰ میلی‌ثانیه‌ای به سیستم جهت خروج امن از لایف‌سایکل جاری و جلوگیری از تداخل با onResume
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            bottomNav.selectedItemId = R.id.nav_home
                        }, 50)
                    } else {
                        parentFragmentManager.executePendingTransactions()
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.nav_host_fragment, HomeFragment())
                            .commitAllowingStateLoss()
                    }
                }
            }
        }
    }

    private fun fetchCategories() {
        loader.visibility = View.VISIBLE
        val repository = com.avapod.app.network.PodcastRepository(requireContext())
        repository.getCategories { categories ->
            activity?.runOnUiThread {
                loader.visibility = View.GONE
                setupRecyclerView(categories)
            }
        }
    }

    private fun setupRecyclerView(list: List<Category>) {
        rvCategories.layoutManager = GridLayoutManager(requireContext(), 2)
        rvCategories.adapter = CategoryAdapter(list) { category ->
            openCategoryDetail(category)
        }
    }

    private fun openCategoryDetail(category: Category) {
        com.avapod.app.utils.AdManager.showAdWithCapping(requireActivity()) {
            val fragment = CategoryPodcastsFragment()
            val bundle = Bundle().apply {
                putString("category_id", category.id)
                putString("category_name", category.name)
            }
            fragment.arguments = bundle

            parentFragmentManager.executePendingTransactions()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commitAllowingStateLoss()
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)?.let { bottomNav ->
            if (bottomNav.selectedItemId != R.id.nav_explore) {
                bottomNav.selectedItemId = R.id.nav_explore
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            if (::rvCategories.isInitialized) {
                rvCategories.adapter = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}