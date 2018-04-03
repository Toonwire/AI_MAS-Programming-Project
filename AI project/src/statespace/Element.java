package statespace;

public class Element {

	private char label;
	
	public Element(char label) {
		this.label = label;
	}
	
	public char getLabel() {
		return this.label;
	}
	
	@Override
	public String toString() {
		return ""+label;
	}
}