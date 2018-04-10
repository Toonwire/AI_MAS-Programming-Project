package statespace;


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
	
	public boolean equals(Pos pos) {
		return this.row == pos.row && this.col == pos.col;
	}
	
	public int manhattanDistanceToPos(Pos p) {
		return Math.abs(this.row - p.row) + Math.abs(this.col - p.col);
	}
}