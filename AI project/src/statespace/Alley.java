package statespace;


import java.util.ArrayList;

public class Alley {
	public Pos opening;
	public ArrayList<Pos> fields;
	
	public Alley(Pos opening, ArrayList<Pos> fields) {
		this.opening = opening;
		this.fields = fields;
	}
}
