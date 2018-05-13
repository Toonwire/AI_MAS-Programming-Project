package statespace;

import java.util.ArrayList;
import java.util.LinkedList;

import sampleclients.MultiCommand;

public class Agent {
	char id;
	String color;
	ArrayList<Box> reachableBoxes;
	ArrayList<Goal> reachableGoals;
	public Agent helper;
	public Agent isHelping = null;
	public Agent getsHelp = null;
	public LinkedList<Pos> avoidList;
	public boolean getHelp = true;
	
	public AIClient client;
	
	public Agent(char id, String color, AIClient client) {
		this.id = id;
		this.color = color;
		this.client = client;
		reachableBoxes = new ArrayList<>();
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

	public ArrayList<Box> getReachableBoxes() {
		return reachableBoxes;
	}

	public void setReachableBoxes(ArrayList<Box> reachableBoxesList) {
		this.reachableBoxes = reachableBoxesList;
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
	
	public LinkedList<Box> getBoxesNotInGoal(Agent a) {
		LinkedList<Box> list = new LinkedList<Box>();
		
		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				 Box b = client.getCurrentState().boxes[row][col];
				 Goal g = client.getGoals()[row][col];
				 
				 if (b != null && a.getReachableBoxes().contains(b)) {
					 if((g == null || Character.toLowerCase(b.getLabel()) != g.getLabel()) && b.getColor() == this.color) {
						 list.add(b);
					 }
				 }
			}
		}
		System.err.println(list);
		return list;
	}
}