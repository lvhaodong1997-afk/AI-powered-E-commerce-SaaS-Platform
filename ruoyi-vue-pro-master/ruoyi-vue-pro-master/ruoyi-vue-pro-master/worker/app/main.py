import logging
from datetime import datetime
from enum import Enum
from typing import Optional

from fastapi import FastAPI
from pydantic import BaseModel, Field
from pythonjsonlogger import jsonlogger


class TkJsonFormatter(jsonlogger.JsonFormatter):

    def process_log_record(self, log_record):
        if "tk_module" in log_record:
            log_record["module"] = log_record.pop("tk_module")
        return super().process_log_record(log_record)


class WorkerTaskType(str, Enum):
    VIDEO_PARSE = "VIDEO_PARSE"
    AI_GENERATION = "AI_GENERATION"
    RENDER = "RENDER"


class WorkerTask(BaseModel):
    traceId: str
    tenantId: int
    companyId: int
    userId: Optional[int] = None
    taskId: Optional[int] = None
    libraryId: Optional[int] = None
    videoId: Optional[int] = None
    type: WorkerTaskType
    payload: dict = Field(default_factory=dict)


logger = logging.getLogger("tk-worker")
handler = logging.StreamHandler()
handler.setFormatter(TkJsonFormatter(
    "%(asctime)s %(levelname)s %(name)s %(message)s "
    "%(traceId)s %(tenantId)s %(companyId)s %(userId)s %(taskId)s "
    "%(libraryId)s %(videoId)s %(tk_module)s %(status)s"
))
logger.addHandler(handler)
logger.setLevel(logging.INFO)

app = FastAPI(title="TK素材工厂 AI Worker", version="0.1.0")


@app.get("/health")
def health():
    return {"status": "ok", "time": datetime.utcnow().isoformat()}


@app.post("/tasks/submit")
def submit_task(task: WorkerTask):
    logger.info(
        "worker task accepted",
        extra={
            "traceId": task.traceId,
            "tenantId": task.tenantId,
            "companyId": task.companyId,
            "userId": task.userId,
            "taskId": task.taskId,
            "libraryId": task.libraryId,
            "videoId": task.videoId,
            # "module" is a reserved LogRecord attribute; the formatter renames this to "module".
            "tk_module": "tk-worker",
            "status": "ACCEPTED",
        },
    )
    # TODO 接入真实队列、FFmpeg、TTS、大模型和状态回写。
    return {"accepted": True, "status": "PENDING"}
