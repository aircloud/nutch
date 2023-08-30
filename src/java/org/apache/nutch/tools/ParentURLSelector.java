package org.apache.nutch.tools;

import java.net.MalformedURLException;
import java.net.URL;

public class ParentURLSelector {
    public static String selectParentURL(String current, String a, String b) throws MalformedURLException {
        // 特殊情况判断
        if (a.equals(b)) return a;
        if (b.isEmpty()) return a;
        if (a.isEmpty()) return b;

        URL currentURL = new URL(current);
        URL aURL = new URL(a);
        URL bURL = new URL(b);

        String currentHost = currentURL.getHost();
        String aHost = aURL.getHost();
        String bHost = bURL.getHost();

        String currentPath = currentURL.getPath();
        String aPath = aURL.getPath();
        String bPath = bURL.getPath();

        // 获取重合部分的长度
        int aHostOverlap = getOverlapLength(currentHost, aHost, true);
        int bHostOverlap = getOverlapLength(currentHost, bHost, true);

        System.out.println("aHostOverlap: " + aHostOverlap + ", aHostOverlap: " + bHostOverlap);
    
        // host 判断逻辑
        if (aHostOverlap == 0 && bHostOverlap == 0) {
            // 都没有和当前重合的，选择短的
            System.out.println("selectShortByHostAndPath");
            return selectShortByHostAndPath(aHost, bHost, aPath, bPath, a, b);
        } else if (aHostOverlap > 0 && bHostOverlap == 0) {
            // a 有和当前重合的，b 没有
            return a;
        } else if (aHostOverlap == 0 && bHostOverlap > 0) {
            // a 没有和当前重合的，b 有
            return b;
        } else {
            // a 和 b 都有和当前重合的
            if (aHostOverlap == bHostOverlap) {
                // 重合长度相同
                if (aHostOverlap < currentHost.split("\\.").length && bHostOverlap < currentHost.split("\\.").length) {
                    // 都没有完全重合
                    return selectShortByHostAndPath(aHost, bHost, aPath, bPath, a, b);
                } else {
                    // 都完全重合
                    return selectPathByOverlap(currentPath, aPath, bPath, a, b);
                }
            } else {
                // 重合长度不同
                return aHostOverlap > bHostOverlap ? a : b;
            }
        }
    }

    // 这里最后的顶级域名，可以考虑不纳入比较范围，不过这样需要引入定义域名列表依赖，暂时先不引入了
    private static int getOverlapLength(String current, String other, Boolean revert) {
        String[] currentParts = current.split("\\.");
        String[] otherParts = other.split("\\.");
        int minLength = Math.min(currentParts.length, otherParts.length);
        int overlap = 0;
        for (int i = 0; i < minLength; i++) {
            int index = revert ? currentParts.length - 1 - i : i;
            int otherIndex = revert ? otherParts.length - 1 - i : i;
            if (currentParts[index].equals(otherParts[otherIndex])) {
                overlap++;
            } else {
                break;
            }
        }
        return overlap;
    }

    private static String selectShortByHostAndPath(String aHost, String bHost, String aPath, String bPath, String a, String b) {
        String[] aHostParts = aHost.split("\\.");
        String[] bHostParts = bHost.split("\\.");
        if (aHostParts.length != bHostParts.length) {
            return aHostParts.length < bHostParts.length ? a : b;
        } else {
            String[] aPathParts = aPath.split("/");
            String[] bPathParts = bPath.split("/");
            if (aPathParts.length != bPathParts.length) {
                return aPathParts.length < bPathParts.length ? a : b;
            } else {
                return aPath.length() < bPath.length() ? a : b;
            }
        }
    }

    private static String selectPathByOverlap(String currentPath, String aPath, String bPath, String a, String b) {
        int aPathOverlap = getOverlapLength(currentPath, aPath, false);
        int bPathOverlap = getOverlapLength(currentPath, bPath, false);

        // path 判断逻辑
        if (aPathOverlap == 0 && bPathOverlap == 0) {
            // 都没有和当前重合的
            return aPath.length() < bPath.length() ? a : b;
        } else if (aPathOverlap > 0 && bPathOverlap == 0) {
            // a 有和当前重合的，b 没有
            return a;
        } else if (aPathOverlap == 0 && bPathOverlap > 0) {
            // a 没有和当前重合的，b 有
            return b;
        } else {
            // a 和 b 都有和当前重合的
            if (aPathOverlap == bPathOverlap) {
                // 重合程度相同
                return a.length() < b.length() ? a : b;
            } else {
                // 选择重合程度更高的
                return aPathOverlap > bPathOverlap ? a : b;
            }
        }
    }
}
