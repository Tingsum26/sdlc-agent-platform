import { TaskStatusBadge, type TaskStatus } from "./TaskStatusBadge.js";

export interface TaskListItem {
  taskId: string;
  title: string;
  repositoryAlias: string;
  status: TaskStatus;
  updatedAt: string;
  stale?: boolean;
}

export function TaskList({ tasks, onOpen }: { tasks: TaskListItem[]; onOpen: (taskId: string) => void }) {
  if (tasks.length === 0) return <p className="sdlc-muted">No workflow tasks yet.</p>;
  return <ul className="sdlc-task-list" aria-label="Workflow tasks">
    {tasks.map((task) => <li key={task.taskId} className="sdlc-card">
      <button type="button" className="sdlc-task-button" onClick={() => onOpen(task.taskId)}
        aria-label={`Open ${task.title}`}>
        <span className="sdlc-task-title">{task.title}</span>
        <span>{task.repositoryAlias}</span>
        <TaskStatusBadge status={task.status} />
        {task.stale && <span className="sdlc-warning">Possibly stale</span>}
        <time dateTime={task.updatedAt}>{new Date(task.updatedAt).toLocaleString()}</time>
      </button>
    </li>)}
  </ul>;
}
