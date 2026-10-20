export interface BoardColumn {
  id: number;
  name: string;
  sort: number;
}

export interface BoardCard {
  id: number;
  name: string;
  desc: string;
  ownerUserid: number;
  pLevel: number;
  completed: boolean;
}

export interface BoardColumnWithCards {
  column: BoardColumn;
  cards: BoardCard[];
}

export interface BoardView {
  projectId: number;
  columns: BoardColumnWithCards[];
}

export interface GanttTask {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  ownerUserid: number;
  pLevel: number;
  completed: boolean;
  milestoneId: number | null;
}

export interface GanttEdge {
  predecessorTaskId: number;
  successorTaskId: number;
}

export interface GanttView {
  projectId: number;
  rangeStart: string;
  rangeEnd: string;
  tasks: GanttTask[];
  edges: GanttEdge[];
}
