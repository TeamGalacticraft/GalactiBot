/*
 * Copyright (c) 2021-2021 Team Galacticraft
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.galacticraft.galactibot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.quarkiverse.githubapp.event.Issue;
import io.quarkiverse.githubapp.event.IssueComment;
import io.quarkiverse.githubapp.event.PullRequest;
import io.quarkiverse.githubapp.event.PullRequestReview;
import org.kohsuke.github.*;

import java.io.IOException;
import java.util.List;

public class Galactibot {
    private static final SimpleCommandExceptionType INVALID_USER = new SimpleCommandExceptionType(new LiteralMessage("Invalid User!"));
    private final CommandDispatcher<GHEventPayload.IssueComment> dispatcher = new CommandDispatcher<>();

    public Galactibot() {
//        dispatcher.register(
//                LiteralArgumentBuilder.<GHEventPayload.IssueComment>literal("request_review").requires(s -> {
//                            try {
//                                return s.getIssue().getState() == GHIssueState.OPEN && s.getIssue().isPullRequest() && s.getComment().getUser().equals(s.getIssue().getUser());
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                            return false;
//                        })
//                        .then(RequiredArgumentBuilder
//                                .<GHEventPayload.IssueComment, String>argument("reviewer", StringArgumentType.word())
//                                .executes(context -> {
//                                    try {
//                                        String reviewer = StringArgumentType.getString(context, "reviewer");
//                                        if (context.getSource().getInstallation().getRoot().getUser(reviewer).isMemberOf(context.getSource().getOrganization())) {
//                                            assert context.getSource().getIssue().isPullRequest();
//                                            context.getSource().getRepository().getPullRequest(context.getSource().getIssue().getNumber());
//                                            return 1;
//                                        }
//                                        throw INVALID_USER.create();
//                                    } catch (IOException e) {
//                                        e.printStackTrace();
//                                    }
//                                    return 0;
//                                })
//                        )
//        );

    }

    /*
     * ISSUE TEMPLATE EXAMPLE
     * ### Mod Loader
     *
     * Fabric
     *
     * ### Version Information
     *
     * Fresh pulled rocket branch
     *
     * ### Log or Crash Report
     *
     * https://gist.github.com/ghost/d8ce80422b0c439983843ef1d75daf63
     *
     * ### Reproduction steps
     *
     * 1. ...
     * 2. ...
     * 3. ...
     */

    void verifyIssueTemplate(@Issue.Opened @Issue.Edited GHEventPayload.Issue payload) throws IOException {
        GHRepository repository = payload.getRepository();
        GHIssue issue = payload.getIssue();
        System.out.println("a");
        if (issue.getState() == GHIssueState.OPEN) {
            if (repository.getFullName().equals(Constant.Repository.GALACTICRAFT) || repository.getFullName().equals(Constant.Repository.TEST_REPO)) {
                if (!payload.getOrganization().hasMember(issue.getUser()) || true) {
                    String body = issue.getBody();
                    int index = body.indexOf("### Version Information");
                    if (index != -1 && body.length() >= index + 27 + 40) {
                        if (repository.getCommit(body.substring(index + 27, index + 27 + 40)) != null) {
                            GHLabel label = repository.getLabel(Constant.Label.INVALID);
                            if (issue.getLabels().contains(label)) {
                                for (GHReaction listReaction : issue.listReactions()) {
                                    if (listReaction.getContent() == ReactionContent.CONFUSED) {
                                        if (listReaction.getUser().getName() == null || listReaction.getUser().getName().equals(Constant.BOT_NAME)) {
                                            issue.removeLabel(Constant.Label.INVALID);
                                            listReaction.delete();
                                            break;
                                        }
                                    }
                                }
                            }
                            return;
                        }
                    }
                } else {
                    return;
                }

            } else {
                return;
            }

            GHLabel label = repository.getLabel(Constant.Label.INVALID);
            if (!issue.getLabels().contains(label)) {
                issue.comment("The version information commit hash is invalid.");
                issue.addLabels(label);
                issue.createReaction(ReactionContent.CONFUSED);
            }

        }
    }

    void executeCommands(@IssueComment.Edited GHEventPayload.IssueComment payload) {
        try {
            if (payload.getIssue().isPullRequest()) {
                String body = payload.getComment().getBody();
                if (body.startsWith(Constant.BOT_NAME)) {
                    dispatcher.execute(body.substring((Constant.BOT_NAME).length() + 1), payload);
                }
            }
        } catch (CommandSyntaxException e) {
            e.printStackTrace();
        }
    }

    void onPRReview(@PullRequestReview.Submitted GHEventPayload.PullRequestReview payload) throws IOException {
        if (payload.getReview().getState() == GHPullRequestReviewState.APPROVED) {
            if (payload.getOrganization().hasMember(payload.getReview().getUser())) {
                GHPullRequest pullRequest = payload.getPullRequest();
                List<GHUser> requestedReviewers = pullRequest.getRequestedReviewers();
                if (requestedReviewers.size() == 0 ||
                        requestedReviewers.size() == 1 && requestedReviewers.get(0).equals(payload.getReview().getUser())) {
                    if (pullRequest.getHead().getCommit().getLastStatus() == null || pullRequest.getHead().getCommit().getLastStatus().getState() == GHCommitState.SUCCESS) {
                        payload.getPullRequest().removeLabels(Constant.Label.READY_FOR_REVIEW, Constant.Label.NEEDS_MORE_WORK);
                        payload.getPullRequest().addLabels(Constant.Label.READY_FOR_MERGE);
                        payload.getPullRequest().createReaction(ReactionContent.ROCKET);
                    }
                }
            }
        } else if (payload.getReview().getState() == GHPullRequestReviewState.CHANGES_REQUESTED) {
            payload.getPullRequest().removeLabels(Constant.Label.READY_FOR_MERGE, Constant.Label.READY_FOR_REVIEW);
            payload.getPullRequest().addLabels(Constant.Label.NEEDS_MORE_WORK);
            payload.getPullRequest().createReaction(ReactionContent.ROCKET).delete();
        } else if (payload.getReview().getState() == GHPullRequestReviewState.DISMISSED) {
            boolean readyForRereview = true;
            for (GHPullRequestReview listReview : payload.getPullRequest().listReviews()) {
                if (listReview.getState() == GHPullRequestReviewState.CHANGES_REQUESTED) {
                    readyForRereview = false;
                    break;
                }
            }
            if (readyForRereview) {
                payload.getPullRequest().removeLabels(Constant.Label.READY_FOR_MERGE, Constant.Label.NEEDS_MORE_WORK);
                payload.getPullRequest().addLabels(Constant.Label.READY_FOR_REVIEW);
            }
        }
    }

    void onPRReviewDismissed(@PullRequest.ReviewRequested GHEventPayload.PullRequest payload) throws IOException {
        boolean readyForRereview = true;
        for (GHPullRequestReview listReview : payload.getPullRequest().listReviews()) {
            if (listReview.getState() == GHPullRequestReviewState.CHANGES_REQUESTED) {
                if (payload.getPullRequest().getRequestedReviewers().contains(listReview.getUser())) continue;
                readyForRereview = false;
                break;
            }
        }
        if (readyForRereview) {
            payload.getPullRequest().removeLabels(Constant.Label.READY_FOR_MERGE, Constant.Label.NEEDS_MORE_WORK);
            payload.getPullRequest().addLabels(Constant.Label.READY_FOR_REVIEW);
        }
    }
}
