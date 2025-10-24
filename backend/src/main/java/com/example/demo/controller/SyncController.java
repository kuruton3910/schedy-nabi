package com.example.demo.controller;

import com.example.demo.dto.AssignmentEntry;
import com.example.demo.dto.CourseEntry;
import com.example.demo.dto.NextClassCard;
import com.example.demo.dto.SyncResult;
import com.example.demo.service.JobManagerService;
import com.example.demo.service.JobManagerService.LoginJob;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * フロントエンドからの非同期同期リクエストを受け付けるAPIコントローラ。
 */
@RestController
@RequestMapping("/api/sync") // このコントローラは "/api/sync" で始まるURLを処理します
public class SyncController {

    private final JobManagerService jobManagerService;

    // JobManagerServiceを注入
    public SyncController(JobManagerService jobManagerService) {
        this.jobManagerService = jobManagerService;
    }

    // フロントエンドから受け取るJSONの形式を定義
    public record SyncRequest(String username, String password, Boolean rememberMe) {}
    /**
     * 同期ジョブを開始するAPIエンドポイント。
     * リクエストを受け取ったら、すぐにJob IDを返します。
     * POST http://localhost:8080/api/sync/start
     */
   @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startSync(@RequestBody SyncRequest request) {
        if (request.username == null || request.username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "usernameは必須です。"));
        }

        // rememberMe が null または false の場合にのみパスワードを必須とする
        // rememberMe が true の場合は Cookie 認証を試みるのでパスワードは任意
        boolean isRememberMeFalse = Boolean.FALSE.equals(request.rememberMe()); // 明示的に false かどうか
        if (isRememberMeFalse && (request.password == null || request.password.isBlank())) {
             return ResponseEntity.badRequest().body(Map.of("error", "ログイン状態を記録しない場合はパスワードは必須です。"));
        }

        // rememberMe が null の場合も true として扱う (自動ログイン試行など)
        boolean rememberMeFlag = !isRememberMeFalse; // false でない場合は true とする

        // JobManagerService に渡す password は null でもOKとする
        LoginJob job = jobManagerService.startNewSyncJob(request.username(), request.password(), rememberMeFlag);

        // ステータス202 ACCEPTED（受理された）で、Job IDを返す
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", job.getId()));
    }

    /**
     * ジョブの現在のステータスを確認するAPIエンドポイント。
     * フロントエンドはこれを数秒おきに呼び出します。
     * GET http://localhost:8080/api/sync/status/{jobId}
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        LoginJob job = jobManagerService.getJob(jobId);
        if (job == null) {
            // 指定されたJob IDが見つからなければ404 Not Foundを返す
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResponse(job));
    }

    private JobStatusResponse toResponse(LoginJob job) {
        SyncResult result = job.getResult();
    return new JobStatusResponse(
        job.getId(),
                job.getStatus(),
                job.getStage(),
                job.getMessage(),
                job.getMfaCode(),
                job.getMfaMessage(),
                job.getError(),
                job.getUpdatedAt() != null ? job.getUpdatedAt().toString() : null,
                result != null ? new SyncResultView(
                        result.username(),
                        result.syncedAt(),
                        result.timetable(),
                        result.assignments(),
                        result.nextClass()
                ) : null
        );
    }

    public record JobStatusResponse(
        String jobId,
            String status,
            String stage,
            String message,
            String mfaCode,
            String mfaMessage,
            String error,
            String updatedAt,
            SyncResultView result
    ) {}

    public record SyncResultView(
            String username,
            String syncedAt,
            List<CourseEntry> timetable,
            List<AssignmentEntry> assignments,
            NextClassCard nextClass
    ) {}
}