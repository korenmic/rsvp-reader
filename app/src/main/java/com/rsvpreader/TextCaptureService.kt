package com.rsvpreader

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TextCaptureService : AccessibilityService() {

    private val logBuffer = mutableListOf<String>()
    private var lastCapturedText = ""
    private var screenHeight = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        log("TextCaptureService connected")
        
        val displayMetrics = resources.displayMetrics
        screenHeight = displayMetrics.heightPixels
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                captureVisibleText()
            }
        }
    }

    override fun onInterrupt() {
        log("TextCaptureService interrupted")
    }

    private fun captureVisibleText() {
        try {
            val targetNode = findBottomSourceWindowNode()
            
            if (targetNode != null) {
                val text = extractTextFromNode(targetNode)
                targetNode.recycle()

                if (text.isNotEmpty() && text != lastCapturedText) {
                    lastCapturedText = text
                    RSVPEngine.updateTextBuffer(text)
                    log("Captured ${text.split(Regex("\\s+")).size} words from bottom window")
                }
            } else {
                val rootNode = rootInActiveWindow
                if (rootNode != null && rootNode.packageName?.toString() != packageName) {
                    val text = extractTextFromNode(rootNode)
                    rootNode.recycle()

                    if (text.isNotEmpty() && text != lastCapturedText) {
                        lastCapturedText = text
                        RSVPEngine.updateTextBuffer(text)
                        log("Captured ${text.split(Regex("\\s+")).size} words from fallback")
                    }
                }
            }
        } catch (e: Exception) {
            log("Error capturing text: ${e.message}")
            saveLogsToZip()
        }
    }

    private fun findBottomSourceWindowNode(): AccessibilityNodeInfo? {
        try {
            val windowsList = windows
            if (windowsList == null || windowsList.isEmpty()) {
                return null
            }
            
            val bottomHalfTop = screenHeight / 2
            val bottomHalfRect = Rect(0, bottomHalfTop, Int.MAX_VALUE, screenHeight)
            
            var bestWindow: AccessibilityWindowInfo? = null
            var maxIntersectionArea = 0
            
            for (window in windowsList) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue
                }
                
                val root = window.root
                if (root == null) {
                    continue
                }
                
                val pkgName = root.packageName?.toString()
                if (pkgName == packageName) {
                    root.recycle()
                    continue
                }
                
                val windowBounds = Rect()
                window.getBoundsInScreen(windowBounds)
                
                val intersectionArea = calculateIntersectionArea(windowBounds, bottomHalfRect)
                
                if (intersectionArea > maxIntersectionArea) {
                    maxIntersectionArea = intersectionArea
                    bestWindow?.root?.recycle()
                    bestWindow = window
                } else {
                    root.recycle()
                }
            }
            
            return bestWindow?.root
        } catch (e: Exception) {
            log("Error finding bottom window: ${e.message}")
            return null
        }
    }

    private fun calculateIntersectionArea(rect1: Rect, rect2: Rect): Int {
        val intersect = Rect(rect1)
        if (!intersect.intersect(rect2)) {
            return 0
        }
        return intersect.width() * intersect.height()
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val builder = StringBuilder()
        
        try {
            if (node.text != null && node.text.isNotEmpty()) {
                builder.append(node.text).append(" ")
            }

            if (node.contentDescription != null && node.contentDescription.isNotEmpty()) {
                builder.append(node.contentDescription).append(" ")
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    builder.append(extractTextFromNode(child))
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            log("Error extracting text from node: ${e.message}")
        }

        return builder.toString()
    }

    private fun log(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val logEntry = "$timestamp: $message"
        logBuffer.add(logEntry)
        Log.d("TextCaptureService", message)
        
        if (logBuffer.size > 1000) {
            logBuffer.removeAt(0)
        }
    }

    private fun saveLogsToZip() {
        try {
            val logDir = File(filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(logDir, "rsvp_logs_$timestamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                val entry = ZipEntry("rsvp_log.txt")
                zipOut.putNextEntry(entry)

                OutputStreamWriter(zipOut, Charsets.UTF_8).use { writer ->
                    logBuffer.forEach { line ->
                        writer.write(line)
                        writer.write("\n")
                    }
                    writer.flush()
                }

                zipOut.closeEntry()
            }

            log("Logs saved to: ${zipFile.absolutePath}")
            logBuffer.clear()
        } catch (e: Exception) {
            Log.e("TextCaptureService", "Failed to save logs: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (logBuffer.isNotEmpty()) {
            saveLogsToZip()
        }
    }
}
