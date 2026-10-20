export interface WidgetDef {
  id: string;
  title: string;
  group: string;
  defaultW: number;
  defaultH: number;
}

export interface LayoutItem {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface DashboardView {
  catalog: WidgetDef[];
  layout: LayoutItem[];
  data: Record<string, unknown>;
  status: string;
  statusNote: string;
  layoutSaved: boolean;
}
