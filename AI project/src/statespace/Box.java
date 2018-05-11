package statespace;

public class Box extends Element {
	
	public String color;
	public Pos pos; // Only for initial position!!!
	public Goal goal;
	
	public boolean inWorkingProcess = false;
	
	public Box(char label, String color, Pos pos) {
		super(label);
		this.color = color;
		this.pos = pos;
	}

	public String getColor() {
		return color;
	}
	
	
}