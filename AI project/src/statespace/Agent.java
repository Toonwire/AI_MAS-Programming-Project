package statespace;

import java.util.LinkedList;

import sampleclients.MultiCommand;

public class Agent {
	char id;
	String color;
	LinkedList<Box> reachableBoxes;
	LinkedList<Goal> reachableGoals;
	public Agent helper;
	public Agent isHelping = null;
	public Agent getsHelp = null;
	public LinkedList<Pos> avoidList;
	public boolean getHelp = true;
	public Agent(char id, String color) {
		this.id = id;
		this.color = color;
		reachableBoxes = new LinkedList<>();
		System.err.println("Found " + color + " agent " + id);
	}

	public String act() {
		return new MultiCommand(MultiCommand.type.Pull, MultiCommand.dir.W, MultiCommand.dir.N).toActionString();
	}

	public String getColor() {
		return color;
	}

	public char getLabel() {
		return id;
	}

	public int getID() {
		return Character.getNumericValue(id);
	}

	@Override
	public String toString() {
		return "" + id;
	}

	public LinkedList<Box> getReachableBoxes() {
		return reachableBoxes;
	}

	public void setReachableBoxes(LinkedList<Box> boxes) {
		this.reachableBoxes = boxes;
	}

	public LinkedList<Agent> getHelpers() {
		if(helper == null) {
			return new LinkedList<Agent>();
		}
		LinkedList<Agent> helpers = helper.getHelpers();
		helpers.add(helper);
		return helpers;
	}

	public void setHelper(Agent helper) {
		this.helper = helper;
	}
}