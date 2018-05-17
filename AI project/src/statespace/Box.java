package statespace;

import java.util.ArrayList;

public class Box extends Element {
	
	public String color;

	private Pos dijkstraPos;
	private Integer[][] dijkstra;
	
	public Pos pos; // Only for initial position after updates!!!
	
	public Goal goal;
	
	public boolean inWorkingProcess = false;
	
	public Box(char label, String color, Pos pos) {
		super(label);
		this.pos = pos;
		this.color = color;
		this.pos = pos;
	}
	
	public String getColor() {
		return color;
	}
	
	public Pos getPos() {
		return pos;
	}
	
	@Override
	public String toString() {
		return "" + label;
	}
	
	
	public void updatePos(int row, int col) {		
		this.pos.row = row;
		this.pos.col = col;
	}
	
	public Integer[][] getDijkstra() {
		return pos == dijkstraPos ? this.dijkstra : updateDijkstra();
	}
	
	public Integer[][] updateDijkstra() {
		this.dijkstraPos = new Pos (this.pos.row, this.pos.col);
		this.dijkstra = new Integer[AIClient.MAX_ROW][AIClient.MAX_COL];
		
		ArrayList<Pair<Pos,Pos>> queue = new ArrayList<>();
		queue.add(new Pair<Pos, Pos>(dijkstraPos, null));
		
		while(!queue.isEmpty()) {
			Pair<Pos, Pos> pair = queue.get(0);
			queue.remove(0);
			Pos pos = (Pos) pair.getLeft();
			Pos parentPos = (Pos) pair.getRight();
			
			if (dijkstra[pos.row][pos.col] != null || AIClient.walls[pos.row][pos.col]) continue;
			if (parentPos == null) 
				dijkstra[pos.row][pos.col] = 0;
			else
				dijkstra[pos.row][pos.col] = dijkstra[parentPos.row][parentPos.col] + 1;
			
			if (0 <= pos.row - 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row - 1, pos.col), pos));
			if (AIClient.MAX_ROW > pos.row + 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row + 1, pos.col), pos));
			if (0 <= pos.col- 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row, pos.col - 1), pos));
			if (AIClient.MAX_COL > pos.col + 1) queue.add(new Pair<Pos, Pos>(new Pos(pos.row, pos.col + 1), pos));
		}

		return dijkstra;
	}
	
	
}