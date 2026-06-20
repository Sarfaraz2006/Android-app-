package com.screenmate.ai;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class AssistantAccessibilityService extends AccessibilityService {
    private static AssistantAccessibilityService instance;
    private String lastScreenText = "";

    static boolean isRunning() {
        return instance != null;
    }

    static String getVisibleText() {
        if (instance == null) {
            return "";
        }
        instance.captureVisibleText();
        return instance.lastScreenText;
    }

    static boolean clickText(String text) {
        return instance != null && instance.performClickOnText(text);
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        captureVisibleText();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        captureVisibleText();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    private void captureVisibleText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            lastScreenText = "";
            return;
        }
        List<String> lines = new ArrayList<>();
        collectText(root, lines, 0);
        lastScreenText = joinLimited(lines, 4000);
    }

    private void collectText(AccessibilityNodeInfo node, List<String> lines, int depth) {
        if (node == null || depth > 8) {
            return;
        }
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        String value = text != null && text.length() > 0 ? text.toString() : description == null ? "" : description.toString();
        if (!value.trim().isEmpty()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            lines.add(value.trim() + " [" + bounds.left + "," + bounds.top + "-" + bounds.right + "," + bounds.bottom + "]");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectText(node.getChild(i), lines, depth + 1);
        }
    }

    private String joinLimited(List<String> lines, int maxChars) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() + line.length() + 1 > maxChars) {
                break;
            }
            builder.append(line).append('\n');
        }
        return builder.toString().trim();
    }

    private boolean performClickOnText(String target) {
        if (target == null || target.trim().isEmpty()) {
            return false;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo node = findClickableNode(root, target.toLowerCase());
        if (node == null) {
            return false;
        }
        while (node != null && !node.isClickable()) {
            node = node.getParent();
        }
        return node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private AccessibilityNodeInfo findClickableNode(AccessibilityNodeInfo node, String target) {
        if (node == null) {
            return null;
        }
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        String value = text != null && text.length() > 0 ? text.toString() : description == null ? "" : description.toString();
        if (value.toLowerCase().contains(target)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findClickableNode(node.getChild(i), target);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
