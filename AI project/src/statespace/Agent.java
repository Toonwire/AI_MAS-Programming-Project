package statespace;

import java.util.LinkedList;

import sampleclients.MultiCommand;

public class Agent {
	public static Agent wait;
	char id;
	String color;
	LinkedList<Box> reachableBoxes;
	LinkedList<Goal> reachableGoals;
//	public Agent helper;
	public Agent isHelping = null;
	public Agent getsHelp = null;
	public LinkedList<Pos> avoidList;
	public boolean getHelp = true;
	public boolean helps = false;
	public int waiting = 0;
	
	public AIClient client;
	
	public Agent(char id, String color, AIClient client) {
		this.id = id;
		this.color = color;
		this.client = client;
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

//	public LinkedList<Agent> getHelpers() {
//		if(helper == null) {
//			return new LinkedList<Agent>();
//		}
//		LinkedList<Agent> helpers = helper.getHelpers();
//		helpers.add(helper);
//		return helpers;
//	}

//	public void setHelper(Agent helper) {
//		this.helper = helper;
//	}
	
	public LinkedList<Box> getBoxesNotInGoal() {
		LinkedList<Box> list = new LinkedList<Box>();
		LinkedList<Integer> listCount = new LinkedList<Integer>();
		
		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				 Box b = client.getCurrentSubState().boxes[row][col];
				 Goal g = client.getGoals()[row][col];
				 if (b != null && !b.inWorkingProcess && reachableBoxes.contains(b)) {

					 if((g == null || Character.toLowerCase(b.getLabel()) != g.getLabel()) && b.getColor() == this.color) {
				
						 char bc = Character.toLowerCase(b.getLabel());
						 System.err.println(client.getGoalListMap());
						 if (client.getGoalListMap().containsKey(bc)) {
							 list.add(b);
							 
							 int distance = Integer.MAX_VALUE;
							 
							 for (Goal goal : client.getGoalListMap().get(bc)) {
								 Integer[][] dijkstra = client.getDijkstraMap().get(goal);
								 if (distance > dijkstra[row][col])
									 distance = dijkstra[row][col];
							 }
							 
							 listCount.add(distance);
						 }
					 }
				 }
			}
		}
		
		for (int i = 0; i < list.size(); i++) {
			for (int j = 0; j < list.size() - 1; j++) {
				if (listCount.get(j) > listCount.get(j + 1)) {
					Integer tempCount = listCount.get(j);
					listCount.remove(j);
					listCount.add(j+1, tempCount);
					
					Box temp = list.get(j);
					list.remove(j);
					list.add(j+1, temp);
				}
			}
		}
		
		return list;
	}
}