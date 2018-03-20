package statespace;

import java.awt.Point;

public class Goal extends Element {

	private Point pos;
		
	public Goal(char label, int row, int col) {
		super(label);
		this.pos = new Point(row,col);
	}

	public Point getPos() {
		return this.pos;
	}
	
	public int getDistanceToGoal(Point p) {
		return Math.abs(p.x - pos.x) + Math.abs(p.y - pos.y);
	}
}