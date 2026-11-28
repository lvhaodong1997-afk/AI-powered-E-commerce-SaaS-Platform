# TikTok One assisted publishing

In Video Publishing Center, start a TikTok One task from a completed video or
from the TikTok One tab. Enter the creator username and project name or number.
If a webpage anchor is required, enter its name and confirm in TikTok One that
the anchor was approved and matches the project market before creating the task.

Download the video from the task. In the official TikTok flow, accept the project
before posting, apply the required commercial content disclosure, and link the
new video to the project. Use the official invite link if available; it stays in
the browser dialog and is not sent to the server. The project anchor is managed
in TikTok One, not in this application.

After posting, enter the full `https://www.tiktok.com/@username/video/ID` URL.
Confirm project association and target-market anchor visibility separately.
All three result fields are user-reported, not verified by TikTok. If association
is pending, keep the task and update its result; do not repost the video.

## Admin API

These endpoints use the existing admin authentication and tenant scope. They
are not part of the public publishing OpenAPI.

| Method and path | Purpose |
| --- | --- |
| `POST /admin-api/tk/tiktok-one/publish/tasks` | Create a task using `generationTaskId`, `tiktokUsername`, `projectName`, `anchorRequired`, and, when required, `anchorName` and `anchorApproved`. |
| `GET /admin-api/tk/tiktok-one/publish/tasks` | Page tasks using `pageNo` and `pageSize`. |
| `POST /admin-api/tk/tiktok-one/publish/tasks/{id}/confirm` | Record `videoUrl`, `projectConfirmed`, and `anchorConfirmed`. The same video can be reconfirmed, but it cannot be replaced with a different video. |

Deploy `tk_tiktok_one_publish_mysql.sql` before starting the new backend. It
creates one new table and leaves ordinary TikTok and Shop publishing untouched.
This release does not call the TikTok One Business API or support automatic
publishing, automatic anchor verification, or attaching anchors to old posts.
