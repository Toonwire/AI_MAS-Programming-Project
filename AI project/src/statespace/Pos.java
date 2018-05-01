package statespace;

import java.util.Arrays;

public class Pos{
	public int row, col;
	
	public Pos(int row, int col) {
		this.row = row;
		this.col = col;
	}
	
	@Override
	public String toString() {
		return "("+row+","+col+")";
	}
	
	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		Pos pos = (Pos) obj;
		if (this.row != pos.row || this.col != pos.col)
			return false;
		return true;
	}
	
	public int manhattanDistanceToPos(Pos p) {
		return Math.abs(this.row - p.row) + Math.abs(this.col - p.col);
	}
}