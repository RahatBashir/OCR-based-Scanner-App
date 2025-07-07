package com.rahat.quickscan

import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class PdfPageAdapter(
    private val pages: List<Bitmap>,
    private val onClick: (Bitmap, Int) -> Unit
) : RecyclerView.Adapter<PdfPageAdapter.ViewHolder>() {

    inner class ViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
        fun bind(bitmap: Bitmap, position: Int) {
            imageView.setImageBitmap(bitmap)
            imageView.setOnClickListener {
                onClick(bitmap, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val imageView = ImageView(parent.context)
        imageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        imageView.adjustViewBounds = true
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setPadding(16, 0, 16, 0)
        return ViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(pages[position], position)
    }

    override fun getItemCount(): Int = pages.size
}
