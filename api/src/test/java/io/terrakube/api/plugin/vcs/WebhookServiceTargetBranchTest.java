package io.terrakube.api.plugin.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.terrakube.api.plugin.scheduler.ScheduleJobService;
import io.terrakube.api.plugin.vcs.provider.bitbucket.BitBucketWebhookService;
import io.terrakube.api.plugin.vcs.provider.github.GitHubWebhookService;
import io.terrakube.api.plugin.vcs.provider.gitlab.GitLabWebhookService;
import io.terrakube.api.repository.JobRepository;
import io.terrakube.api.repository.WebhookEventRepository;
import io.terrakube.api.repository.WebhookRepository;
import io.terrakube.api.repository.WorkspaceRepository;
import io.terrakube.api.rs.Organization;
import io.terrakube.api.rs.job.Job;
import io.terrakube.api.rs.job.JobStatus;
import io.terrakube.api.rs.job.JobVia;
import io.terrakube.api.rs.vcs.Vcs;
import io.terrakube.api.rs.vcs.VcsType;
import io.terrakube.api.rs.webhook.Webhook;
import io.terrakube.api.rs.webhook.WebhookEvent;
import io.terrakube.api.rs.webhook.WebhookEventPathType;
import io.terrakube.api.rs.webhook.WebhookEventType;
import io.terrakube.api.rs.workspace.Workspace;

@ExtendWith(MockitoExtension.class)
public class WebhookServiceTargetBranchTest {

    private static final String TEMPLATE_ID = "template-id";

    private WebhookRepository webhookRepository;
    private WebhookEventRepository webhookEventRepository;
    private GitHubWebhookService gitHubWebhookService;
    private GitLabWebhookService gitLabWebhookService;
    private BitBucketWebhookService bitBucketWebhookService;
    private JobRepository jobRepository;
    private ScheduleJobService scheduleJobService;
    private WorkspaceRepository workspaceRepository;

    private WebhookService subject;

    @BeforeEach
    void setup() throws Exception {
        webhookRepository = mock(WebhookRepository.class);
        webhookEventRepository = mock(WebhookEventRepository.class);
        gitHubWebhookService = mock(GitHubWebhookService.class);
        gitLabWebhookService = mock(GitLabWebhookService.class);
        bitBucketWebhookService = mock(BitBucketWebhookService.class);
        jobRepository = mock(JobRepository.class);
        scheduleJobService = mock(ScheduleJobService.class);
        workspaceRepository = mock(WorkspaceRepository.class);

        subject = new WebhookService(
                webhookRepository,
                webhookEventRepository,
                gitHubWebhookService,
                gitLabWebhookService,
                bitBucketWebhookService,
                jobRepository,
                scheduleJobService,
                new ObjectMapper(),
                workspaceRepository);
    }

    @Test
    void pullRequestPreviewUsesTargetBranchWhenEnabled() throws Exception {
        Workspace workspace = createWorkspace();
        Webhook webhook = createWebhook(workspace, true);
        WebhookEvent webhookEvent = createPullRequestEvent(webhook, "master");
        WebhookResult webhookResult = createPullRequestResult("development", "master");

        when(webhookRepository.getReferenceById(webhook.getId())).thenReturn(webhook);
        when(webhookEventRepository.findByWebhookAndEventOrderByPriorityAsc(webhook, WebhookEventType.PULL_REQUEST))
                .thenReturn(List.of(webhookEvent));
        when(gitHubWebhookService.processWebhook(anyString(), anyMap(), anyString(), eq(workspace.getVcs())))
                .thenReturn(webhookResult);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            if (job.getId() == 0) {
                job.setId(101);
            }
            return job;
        });

        subject.processWebhook(webhook.getId().toString(), "{}", Map.of());

        ArgumentCaptor<Job> savedJobs = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository, times(1)).save(savedJobs.capture());
        verify(scheduleJobService, times(1)).createJobContext(any(Job.class));

        Job savedJob = savedJobs.getValue();
        assertEquals("master", savedJob.getOverrideBranch());
    }

    @Test
    void pullRequestPreviewUsesSourceBranchWhenTargetBranchToggleDisabled() throws Exception {
        Workspace workspace = createWorkspace();
        Webhook webhook = createWebhook(workspace, false);
        WebhookEvent webhookEvent = createPullRequestEvent(webhook, "master");
        WebhookResult webhookResult = createPullRequestResult("development", "master");

        when(webhookRepository.getReferenceById(webhook.getId())).thenReturn(webhook);
        when(webhookEventRepository.findByWebhookAndEventOrderByPriorityAsc(webhook, WebhookEventType.PULL_REQUEST))
                .thenReturn(List.of(webhookEvent));
        when(gitHubWebhookService.processWebhook(anyString(), anyMap(), anyString(), eq(workspace.getVcs())))
                .thenReturn(webhookResult);

        subject.processWebhook(webhook.getId().toString(), "{}", Map.of());

        verify(jobRepository, never()).save(any(Job.class));
        verify(scheduleJobService, never()).createJobContext(any(Job.class));
        verify(gitHubWebhookService, never()).sendCommitStatus(any(Job.class), eq(JobStatus.pending));
    }

    @Test
    void pullRequestPlanUsesTargetBranchWhenEnabled() throws Exception {
        Workspace workspace = createWorkspace();
        Webhook webhook = createWebhook(workspace, true);
        WebhookEvent webhookEvent = createPullRequestEvent(webhook, "master");
        webhookEvent.setPrWorkflowEnabled(true);

        WebhookResult webhookResult = createPullRequestResult("development", "master");
        webhookResult.setPrComment(true);
        webhookResult.setCommentCommand("plan");

        when(webhookRepository.getReferenceById(webhook.getId())).thenReturn(webhook);
        when(webhookEventRepository.findByWebhookAndEventOrderByPriorityAsc(webhook, WebhookEventType.PULL_REQUEST))
                .thenReturn(List.of(webhookEvent));
        when(gitHubWebhookService.processWebhook(anyString(), anyMap(), anyString(), eq(workspace.getVcs())))
                .thenReturn(webhookResult);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            if (job.getId() == 0) {
                job.setId(101);
            }
            return job;
        });

        subject.processWebhook(webhook.getId().toString(), "{}", Map.of());

        ArgumentCaptor<Job> savedJobs = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository, times(2)).save(savedJobs.capture());
        verify(scheduleJobService, times(1)).createJobContext(any(Job.class));
        verify(gitHubWebhookService, times(1)).sendCommitStatus(any(Job.class), eq(JobStatus.pending));

        List<Job> persistedJobs = savedJobs.getAllValues();
        Job savedJob = persistedJobs.get(persistedJobs.size() - 1);
        assertEquals("master", savedJob.getOverrideBranch());
        assertEquals(12, savedJob.getPrNumber());
    }

    @Test
    void pullRequestApplyKeepsUsingSourceBranchWhenEnabled() throws Exception {
        Workspace workspace = createWorkspace();
        workspace.setDefaultTemplate("default-template");

        Webhook webhook = createWebhook(workspace, true);
        WebhookEvent webhookEvent = createPullRequestEvent(webhook, "master");
        webhookEvent.setPrWorkflowEnabled(true);
        WebhookResult webhookResult = createPullRequestResult("development", "master");
        webhookResult.setPrComment(true);
        webhookResult.setCommentCommand("apply");

        when(webhookRepository.getReferenceById(webhook.getId())).thenReturn(webhook);
        when(webhookEventRepository.findByWebhookAndEventOrderByPriorityAsc(webhook, WebhookEventType.PULL_REQUEST))
                .thenReturn(List.of(webhookEvent));
        when(gitHubWebhookService.processWebhook(anyString(), anyMap(), anyString(), eq(workspace.getVcs())))
                .thenReturn(webhookResult);
        when(workspaceRepository.save(workspace)).thenReturn(workspace);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            if (job.getId() == 0) {
                job.setId(101);
            }
            return job;
        });

        subject.processWebhook(webhook.getId().toString(), "{}", Map.of());

        ArgumentCaptor<Job> savedJobs = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository, times(2)).save(savedJobs.capture());
        verify(scheduleJobService, times(1)).createJobContext(any(Job.class));

        List<Job> persistedJobs = savedJobs.getAllValues();
        Job savedJob = persistedJobs.get(persistedJobs.size() - 1);
        assertEquals("development", savedJob.getOverrideBranch());
        assertTrue(savedJob.isAutoApply());
        assertEquals(12, savedJob.getPrNumber());
    }

    private Workspace createWorkspace() {
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("test-organization");

        Vcs vcs = new Vcs();
        vcs.setVcsType(VcsType.GITHUB);

        Workspace workspace = new Workspace();
        workspace.setId(UUID.randomUUID());
        workspace.setName("workspace-under-test");
        workspace.setOrganization(organization);
        workspace.setVcs(vcs);
        workspace.setBranch("main");
        return workspace;
    }

    private Webhook createWebhook(Workspace workspace, boolean prPreviewTargetBranch) {
        Webhook webhook = new Webhook();
        webhook.setId(UUID.randomUUID());
        webhook.setWorkspace(workspace);
        webhook.setPrPreviewTargetBranch(prPreviewTargetBranch);
        return webhook;
    }

    private WebhookEvent createPullRequestEvent(Webhook webhook, String branch) {
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setId(UUID.randomUUID());
        webhookEvent.setWebhook(webhook);
        webhookEvent.setEvent(WebhookEventType.PULL_REQUEST);
        webhookEvent.setBranch(branch);
        webhookEvent.setPath("terraform/*");
        webhookEvent.setPathType(WebhookEventPathType.PATTERN);
        webhookEvent.setTemplateId(TEMPLATE_ID);
        webhookEvent.setPriority(1);
        return webhookEvent;
    }

    private WebhookResult createPullRequestResult(String sourceBranch, String targetBranch) {
        WebhookResult webhookResult = new WebhookResult();
        webhookResult.setValid(true);
        webhookResult.setEvent("pull_request");
        webhookResult.setBranch(sourceBranch);
        webhookResult.setTargetBranch(targetBranch);
        webhookResult.setFileChanges(List.of("terraform/main.tf"));
        webhookResult.setCommit("abcdef123");
        webhookResult.setPrNumber(12);
        webhookResult.setCreatedBy("test-user");
        webhookResult.setVia(JobVia.Github.name());
        return webhookResult;
    }
}
