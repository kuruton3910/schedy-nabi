package com.example.demo.service;

import com.example.demo.dto.Assignment;
import com.example.demo.dto.Course;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 大学のWebサイトから情報をスクレイピングする責務を持つServiceクラス。
 */
@Service
public class ScrapingService {

    private static final Logger log = LoggerFactory.getLogger(ScrapingService.class);

    /**
     * 指定されたCookieを使用してログイン後のホームページから未提出の課題を全て取得します。
     * @param cookies ログイン後のセッションCookie
     * @return 課題のリスト
     * @throws IOException ページの取得に失敗した場合
     */
    public List<Assignment> getAllAssignments(Map<String, String> cookies) throws IOException {
        List<Assignment> allAssignments = new ArrayList<>();
        String homeCourseUrl = "https://ct.ritsumei.ac.jp/ct/home_course";
        
        Document homeDoc = Jsoup.connect(homeCourseUrl).cookies(cookies).get();
        log.debug("マイページ取得成功！ タイトル: {}", homeDoc.title());

        List<String> courseUrls = findCourseUrlsWithAssignments(homeDoc);
        for (String courseUrl : courseUrls) {
            log.debug("詳細を取得中: {}", courseUrl);
            Document coursePageDoc = Jsoup.connect(courseUrl).cookies(cookies).get();
            String courseName = coursePageDoc.selectFirst("#coursename").text();
            Map<String, String> categoryUrls = findAssignmentCategoryUrls(coursePageDoc);

            for (Map.Entry<String, String> category : categoryUrls.entrySet()) {
                String categoryName = category.getKey();
                String categoryUrl = category.getValue();
                Document assignmentListDoc = Jsoup.connect(categoryUrl).cookies(cookies).get();
                List<Assignment> details = extractAssignmentDetails(assignmentListDoc, courseName, categoryName);
                allAssignments.addAll(details);
            }
        }
        return allAssignments;
    }

    /**
     * 指定されたCookieを使用して時間割ページを解析し、授業のリストを返します。
     * @param cookies ログイン後のセッションCookie
     * @return 授業のリスト
     * @throws IOException ページの取得に失敗した場合
     */
    public List<Course> parseTimetableToList(Map<String, String> cookies) throws IOException {
        // 時間割ページのURLは実際のURLに合わせてください
        String timetableUrl = "https://ct.ritsumei.ac.jp/ct/home_course";
        Document doc = Jsoup.connect(timetableUrl).cookies(cookies).get();

        log.debug("【時間割の解析を開始】");
        List<Course> courseList = new ArrayList<>();
        Element timetable = doc.selectFirst("#courselistweekly table.stdlist");
        if (timetable == null) {
            log.warn("時間割テーブルが見つかりませんでした。");
            return courseList;
        }

        Map<Integer, Integer> rowspanCounters = new HashMap<>();
        Map<Integer, List<Map<String, String>>> rowspanData = new HashMap<>();
        String[] days = {"月", "火", "水", "木", "金", "土"};

        for (Element row : timetable.select("tbody tr:has(td.period)")) {
            String period = row.selectFirst("td.period").text();
            if (period.equals("他")) continue;

            Elements cells = row.select("td.course");
            int cellIndex = 0;
            for (int dayIndex = 0; dayIndex < days.length; dayIndex++) {
                if (rowspanCounters.getOrDefault(dayIndex, 0) > 0) {
                    List<Map<String, String>> storedCourses = rowspanData.get(dayIndex);
                    for (Map<String, String> data : storedCourses) {
                        courseList.add(new Course(days[dayIndex], period, data.get("name"), data.get("location")));
                    }
                    rowspanCounters.put(dayIndex, rowspanCounters.get(dayIndex) - 1);
                } else if (cellIndex < cells.size()) {
                    Element cell = cells.get(cellIndex++);
                    if (cell.hasClass("course-cell")) {
                        Elements courseDivs = cell.select("div[onclick*='course_']");
                        List<Map<String, String>> currentCellCourses = new ArrayList<>();
                        for(Element courseDiv : courseDivs) {
                            String courseName = courseDiv.selectFirst("a").text();
                            String rawLocation = courseDiv.selectFirst(".couraselocationinfoV2").text();                            
                            String location = rawLocation; 
                            int halfWidthIndex = rawLocation.indexOf(":");
                            int fullWidthIndex = rawLocation.indexOf("：");                         
                            int splitIndex = -1;                            
                            if (halfWidthIndex != -1 && fullWidthIndex != -1) {
                                splitIndex = Math.min(halfWidthIndex, fullWidthIndex);
                            } else if (halfWidthIndex != -1) {
                            splitIndex = halfWidthIndex;
                            } else if (fullWidthIndex != -1) {
                                splitIndex = fullWidthIndex;
                            }                           
                            if (splitIndex != -1) {
                                location = rawLocation.substring(splitIndex + 1);
                            }
                            courseList.add(new Course(days[dayIndex], period, courseName, location));
                            Map<String, String> data = new HashMap<>();
                            data.put("name", courseName);
                            data.put("location", location);
                            currentCellCourses.add(data);
                        }
                        if (cell.hasAttr("rowspan")) {
                            int spanCount = Integer.parseInt(cell.attr("rowspan"));
                            if (spanCount > 1) {
                                rowspanCounters.put(dayIndex, spanCount - 1);
                                rowspanData.put(dayIndex, currentCellCourses);
                            }
                        }
                    }
                }
            }
        }
        log.debug ("【時間割の解析が完了】 {}件の授業情報を取得しました。", courseList.size());
        return courseList;
    }

    // --- Private Helper Methods (元のコードから移植) ---

    private List<String> findCourseUrlsWithAssignments(Document homeDoc) {
        log.debug("【ステップ1】未提出課題があるコースのURLを検索中...");
        List<String> courseUrls = new ArrayList<>();
        Elements courseDivs = homeDoc.select("div.coursestatus:has(img[src*='icon-coursedeadline-on.png'])");
        for (Element statusDiv : courseDivs) {
            Element courseBlock = statusDiv.closest("div[onclick*='course_']");
            if (courseBlock != null) {
                Element link = courseBlock.selectFirst("a");
                if (link != null) {
                    courseUrls.add(link.absUrl("href"));
                }
            }
        }
        log.debug(" -> {}件のコースで課題を発見。", courseUrls.size());
        return courseUrls;
    }

    private Map<String, String> findAssignmentCategoryUrls(Document coursePageDoc) {
        String courseName = coursePageDoc.selectFirst("#coursename").text();
        log.debug("【ステップ2】コース「{}」で課題カテゴリを検索中...", courseName);
        Map<String, String> categoryUrls = new HashMap<>();
        Map<String, String> categories = Map.of(
            "レポート", ".course-menu-report",
            "アンケート", ".course-menu-survey",
            "小テスト", ".course-menu-query"
        );
        for (Map.Entry<String, String> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            String selector = entry.getValue();
            Element menu = coursePageDoc.selectFirst(selector + ":has(span.my-unreadcount)");
            if (menu != null) {
                Element link = menu.selectFirst("a");
                if (link != null) {
                    String categoryUrl = link.absUrl("href");
                    categoryUrls.put(categoryName, categoryUrl);
                    log.debug(" -> 未提出あり: {} (URL: {})", categoryName, categoryUrl);
                }
            }
        }
        return categoryUrls;
    }

    private List<Assignment> extractAssignmentDetails(Document assignmentListPageDoc, String courseName, String category) {
        log.debug("【ステップ3】「{}」一覧ページから詳細を抽出中...", category);
        List<Assignment> detailedAssignments = new ArrayList<>();
        Elements rows = assignmentListPageDoc.select("table.stdlist tr:has(span.deadline:contains(未提出))");
        for (Element row : rows) {
            Element titleLink = null;
            if ("レポート".equals(category)) {
                titleLink = row.selectFirst("h3.report-title a");
            } else if ("アンケート".equals(category) || "小テスト".equals(category)) {
                titleLink = row.selectFirst("td.query-title a");
            }
            Element deadlineCell = row.selectFirst("td.center:last-of-type");

            if (titleLink != null && deadlineCell != null) {
                String title = titleLink.text();
                String deadline = deadlineCell.text();
                String url = titleLink.absUrl("href");
                detailedAssignments.add(new Assignment(courseName, category, title, deadline, url));
            }
        }
       log.debug(" -> {}件の詳細情報を抽出。", detailedAssignments.size());
        return detailedAssignments;
    }
}