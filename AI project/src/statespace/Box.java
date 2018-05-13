package statespace;

public class Box extends Element {
	
	public String color;
	private Pos pos;
	
	public Box(char label, String color, Pos pos) {
		super(label);
		this.pos = pos;
		this.color = color;
	}
	
	public String getColor() {
		return color;
	}
	
	public Pos getPos() {
		return pos;
	}
	
	
}