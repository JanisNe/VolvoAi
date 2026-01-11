package com.example.volvoai.yolo

import kotlin.math.max
import kotlin.math.min

data class DetBox(
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    val score: Float, val cls: Int
)

fun iou(a: DetBox, b: DetBox): Float {
    val xx1 = maxOf(a.x1, b.x1)
    val yy1 = maxOf(a.y1, b.y1)
    val xx2 = minOf(a.x2, b.x2)
    val yy2 = minOf(a.y2, b.y2)
    val w = max(0f, xx2 - xx1)
    val h = max(0f, yy2 - yy1)
    val inter = w * h
    val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
    val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
    val uni = areaA + areaB - inter
    return if (uni <= 0f) 0f else inter / uni
}

fun nms(boxes: List<DetBox>, iouTh: Float): List<DetBox> {
    val sorted = boxes.sortedByDescending { it.score }.toMutableList()
    val keep = mutableListOf<DetBox>()
    while (sorted.isNotEmpty()) {
        val a = sorted.removeAt(0)
        keep.add(a)
        val it = sorted.iterator()
        while (it.hasNext()) {
            val b = it.next()
            if (iou(a, b) > iouTh && a.cls == b.cls) it.remove()
        }
    }
    return keep
}

/**
 * Decodes YOLOv8 output (rows x (5 + C)) into boxes in input-image coordinates.
 * If your export outputs normalized cx,cy,w,h in [0,1], set norm=true. If in pixels, norm=false.
 */
fun decodeYolov8(
    out: FloatArray,
    rows: Int,
    numClasses: Int,
    inW: Int,
    inH: Int,
    confTh: Float,
    norm: Boolean
): List<DetBox> {
    val stride = 5 + numClasses
    val res = ArrayList<DetBox>(rows)
    for (i in 0 until rows) {
        val off = i * stride
        val cx = out[off + 0]
        val cy = out[off + 1]
        val w  = out[off + 2]
        val h  = out[off + 3]
        val obj = out[off + 4]
        var bestC = 0
        var bestP = Float.NEGATIVE_INFINITY
        for (c in 0 until numClasses) {
            val p = out[off + 5 + c]
            if (p > bestP) { bestP = p; bestC = c }
        }
        val score = obj * bestP
        if (score < confTh) continue
        val (cxp, cyp, wp, hp) = if (norm) {
            floatArrayOf(cx * inW, cy * inH, w * inW, h * inH)
        } else {
            floatArrayOf(cx, cy, w, h)
        }
        val x1 = cxp - wp / 2f
        val y1 = cyp - hp / 2f
        val x2 = cxp + wp / 2f
        val y2 = cyp + hp / 2f
        res.add(DetBox(x1, y1, x2, y2, score, bestC))
    }
    return res
}
