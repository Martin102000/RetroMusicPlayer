/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.fragments.base

import android.os.Bundle
import android.view.*
import androidx.annotation.NonNull
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.appthemehelper.common.ATHToolbarActivity
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentMainRecyclerBinding
import code.name.monkey.retromusic.dialogs.CreatePlaylistDialog
import code.name.monkey.retromusic.dialogs.ImportPlaylistDialog
import code.name.monkey.retromusic.extensions.accentColor
import code.name.monkey.retromusic.extensions.dip
import code.name.monkey.retromusic.extensions.drawNextToNavbar
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.util.ThemedFastScroller.create
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder

abstract class AbsRecyclerViewFragment<A : RecyclerView.Adapter<*>, LM : RecyclerView.LayoutManager> :
    AbsMainActivityFragment(R.layout.fragment_main_recycler) {

    private var _binding: FragmentMainRecyclerBinding? = null
    private val binding get() = _binding!!
    protected var adapter: A? = null
    protected var layoutManager: LM? = null
    val shuffleButton get() = binding.shuffleButton
    abstract val isShuffleVisible: Boolean

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMainRecyclerBinding.bind(view)
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        enterTransition = MaterialFadeThrough().apply {
            addTarget(binding.recyclerView)
        }
        mainActivity.setSupportActionBar(binding.toolbar)
        mainActivity.supportActionBar?.title = null
        initLayoutManager()
        initAdapter()
        setUpRecyclerView()
        setupToolbar()
        // Add listeners when shuffle is visible
        if (isShuffleVisible) {
            binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy > 0) {
                        binding.shuffleButton.hide()
                    } else if (dy < 0) {
                        binding.shuffleButton.show()
                    }

                }
            })
            binding.shuffleButton.apply {
                setOnClickListener {
                    onShuffleClicked()
                }
                accentColor()
            }
        } else {
            binding.shuffleButton.isVisible = false
        }
        libraryViewModel.getFabMargin().observe(viewLifecycleOwner, {
            binding.shuffleButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = it
            }
        })
    }

    open fun onShuffleClicked() {
    }

    fun toolbar(): Toolbar {
        return binding.toolbar
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true).addTarget(requireView())
            reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false)
            findNavController().navigate(
                R.id.searchFragment,
                null,
                navOptions
            )
        }
        val appName = resources.getString(titleRes)
        binding.appNameText.text = appName
        binding.toolbarContainer.drawNextToNavbar()
        binding.appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())
    }

    abstract val titleRes: Int

    private fun setUpRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = this@AbsRecyclerViewFragment.layoutManager
            adapter = this@AbsRecyclerViewFragment.adapter
            create(this)
        }
        checkForPadding()
    }

    protected open fun createFastScroller(recyclerView: RecyclerView): FastScroller {
        return FastScrollerBuilder(recyclerView).useMd2Style().build()
    }

    private fun initAdapter() {
        adapter = createAdapter()
        adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                checkIsEmpty()
                checkForPadding()
            }
        })
    }

    protected open val emptyMessage: Int
        @StringRes get() = R.string.empty

    private fun getEmojiByUnicode(unicode: Int): String {
        return String(Character.toChars(unicode))
    }

    private fun checkIsEmpty() {
        binding.emptyText.setText(emptyMessage)
        binding.empty.visibility = if (adapter!!.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun checkForPadding() {
        val itemCount: Int = adapter?.itemCount ?: 0

        if (itemCount > 0 && MusicPlayerRemote.playingQueue.isNotEmpty()) {
            binding.recyclerView.updatePadding(bottom = dip(R.dimen.mini_player_height_expanded))
        } else {
            binding.recyclerView.updatePadding(bottom = dip(R.dimen.mini_player_height))
        }
    }

    private fun initLayoutManager() {
        layoutManager = createLayoutManager()
    }

    protected abstract fun createLayoutManager(): LM

    @NonNull
    protected abstract fun createAdapter(): A

    override fun onQueueChanged() {
        super.onQueueChanged()
        checkForPadding()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        checkForPadding()
    }

    protected fun invalidateLayoutManager() {
        initLayoutManager()
        binding.recyclerView.layoutManager = layoutManager
    }

    protected fun invalidateAdapter() {
        initAdapter()
        checkIsEmpty()
        binding.recyclerView.adapter = adapter
    }

    val recyclerView get() = binding.recyclerView

    val container get() = binding.root

    fun scrollToTop() {
        recyclerView.scrollToPosition(0)
        binding.appBarLayout.setExpanded(true, true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        ToolbarContentTintHelper.handleOnPrepareOptionsMenu(requireActivity(), binding.toolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_main, menu)
        ToolbarContentTintHelper.handleOnCreateOptionsMenu(
            requireContext(),
            binding.toolbar,
            menu,
            ATHToolbarActivity.getToolbarBackgroundColor(binding.toolbar)
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> findNavController().navigate(
                R.id.settingsActivity,
                null,
                navOptions
            )
            R.id.action_import_playlist -> ImportPlaylistDialog().show(
                childFragmentManager,
                "ImportPlaylist"
            )
            R.id.action_add_to_playlist -> CreatePlaylistDialog.create(emptyList()).show(
                childFragmentManager,
                "ShowCreatePlaylistDialog"
            )
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
