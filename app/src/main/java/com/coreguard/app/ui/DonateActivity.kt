package com.coreguard.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.coreguard.app.R

class DonateActivity : AppCompatActivity() {

    private data class Item(
        val title: String,
        val address: String,
        val qrContent: String,
        val showAddress: Boolean = true
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donate)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val items = listOf(
            Item(
                title = getString(R.string.donate_card),
                address = getString(R.string.donate_card_number),
                qrContent = getString(R.string.donate_card_number)
            ),
            Item(
                title = getString(R.string.donate_btc),
                address = getString(R.string.donate_btc_address),
                qrContent = "bitcoin:${getString(R.string.donate_btc_address)}"
            ),
            Item(
                title = getString(R.string.donate_usdc),
                address = getString(R.string.donate_usdc_address),
                qrContent = getString(R.string.donate_usdc_address)
            ),
            Item(
                title = getString(R.string.donate_xmr),
                address = getString(R.string.donate_xmr_address),
                qrContent = "monero:${getString(R.string.donate_xmr_address)}"
            )
        )

        val container = findViewById<LinearLayout>(R.id.donateContainer)
        items.forEachIndexed { index, item ->
            if (index > 0) {
                container.addView(Space(this).also {
                    it.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(12)
                    )
                })
            }
            container.addView(buildCard(item))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun buildCard(item: Item): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            radius = dp(12).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(getColor(R.color.card_bg))
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        card.addView(inner)

        // Title
        inner.addView(TextView(this).apply {
            text = item.title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.primary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(14) }
        })

        // QR code
        val qrSizePx = dp(200)
        try {
            inner.addView(ImageView(this).apply {
                setImageBitmap(generateQR(item.qrContent, qrSizePx))
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(qrSizePx, qrSizePx).also {
                    it.bottomMargin = if (item.showAddress || item.address.isEmpty()) dp(4) else dp(14)
                }
            })
        } catch (_: Exception) {}

        // Address text + copy button
        if (item.showAddress && item.address.isNotEmpty()) {
            inner.addView(TextView(this).apply {
                text = item.address
                textSize = 13f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setTextIsSelectable(true)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.topMargin = dp(10)
                    it.bottomMargin = dp(12)
                }
            })

            inner.addView(MaterialButton(this).apply {
                text = getString(R.string.donate_copy)
                setOnClickListener {
                    copyToClipboard(item.title, item.address)
                    Toast.makeText(context, getString(R.string.donate_copied), Toast.LENGTH_SHORT).show()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }

        return card
    }

    private fun generateQR(content: String, sizePx: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
