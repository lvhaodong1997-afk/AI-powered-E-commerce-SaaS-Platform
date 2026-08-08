const TERMINAL_GENERATION_STATUSES = new Set(['SUCCESS', 'FAILED'])

export const isTerminalGenerationStatus = (status) =>
  TERMINAL_GENERATION_STATUSES.has(status || '')

export const mergeGenerationTasks = (existingTasks, newTaskIds) => {
  const taskMap = new Map()
  existingTasks.forEach((task) => {
    if (task?.id) {
      taskMap.set(task.id, task)
    }
  })
  newTaskIds.forEach((id) => {
    const numericId = Number(id)
    if (numericId && !Number.isNaN(numericId) && !taskMap.has(numericId)) {
      taskMap.set(numericId, { id: numericId, status: 'PENDING', progress: 0 })
    }
  })
  return [...taskMap.values()]
}

export const getGenerationFocusTask = (tasks) =>
  tasks.find((task) => !isTerminalGenerationStatus(task.status)) ||
  tasks.find((task) => task.status === 'FAILED') ||
  tasks[tasks.length - 1]
