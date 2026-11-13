package io.github.kentprodan.mediatheq.core.ui

import io.github.kentprodan.mediatheq.core.common.QualityKind

/** The label's name ("Hi-Res Lossless"). */
val QualityKind.text: Int
    get() =
        when (this) {
            QualityKind.DolbyAtmos -> R.string.core_ui_quality_atmos
            QualityKind.HiResLossless -> R.string.core_ui_quality_hi_res
            QualityKind.Lossless -> R.string.core_ui_quality_lossless
            QualityKind.HighQuality -> R.string.core_ui_quality_high
            QualityKind.HighEfficiency -> R.string.core_ui_quality_efficient
        }
