package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TkGenerationTaskLeaseService {

    private final TkGenerationTaskMapper taskMapper;

    @Autowired
    public TkGenerationTaskLeaseService(TkGenerationTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public boolean claim(Long taskId, String leaseToken, String workerId,
                         LocalDateTime staleBefore, LocalDateTime leaseExpireTime) {
        return taskMapper.claimTask(taskId, leaseToken, workerId, staleBefore, leaseExpireTime) == 1;
    }

    public boolean renew(Long taskId, String leaseToken, LocalDateTime leaseExpireTime) {
        return taskMapper.renewTaskLease(taskId, leaseToken, leaseExpireTime) == 1;
    }

    public void release(Long taskId, String leaseToken) {
        taskMapper.releaseTaskLease(taskId, leaseToken);
    }
}
