package statespace;

import java.util.ArrayList;

import sampleclients.MultiCommand;

public class Agent {
	public static Agent wait;
	char id;
	String color;
	public ArrayList<Box> reachableBoxes = new ArrayList<>();;
	public ArrayList<Goal> reachableGoals = new ArrayList<>();;
	public Agent helper;
	public Agent isHelping = null;
	public Agent getsHelp = null;
	public ArrayList<Pos> avoidList;
	public boolean getHelp = true;
	public boolean helps = false;
	public int waiting = 0;
	
	public AIClient client;
	
	public Agent(char id, String color, AIClient client) {
		this.id = id;
		this.color = color;
		this.client = client;
		reachableBoxes = new ArrayList<>();
		reachableGoals = new ArrayList<>();
		System.err.println("Found " + color + " agent " + id);
		System.err.println(reachableGoals);
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

	public void setReachableBoxes(ArrayList<Box> boxes) { 
		if (boxes != null) this.reachableBoxes = boxes;		
	}

	public ArrayList<Goal> getReachableGoals() {
		return reachableGoals;
	}

	public void setReachableGoals(ArrayList<Goal> goals) { 
		if (goals != null) this.reachableGoals = goals;
	}
	
	public void setHelper(Agent helper) {
		this.helper = helper;
	}
	
	public ArrayList<Box> getBoxesNotInGoal() {
		ArrayList<Box> list = new ArrayList<Box>();
		ArrayList<Integer> listCount = new ArrayList<Integer>();
		
		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				 Box b = client.getCurrentSubState().boxes[row][col];
				 Goal g = client.getGoals()[row][col];

				//Box is reachable and not in use
				if (b != null && !b.inWorkingProcess && reachableBoxes.contains(b)) {
					//Box is not already in goal
					char bc = Character.toLowerCase(b.getLabel());
					if((g == null || bc != g.getLabel())) {// && b.getColor() == this.color) {
						//Box has a goal 
						if (client.getGoalListMap().containsKey(bc)) {							 
							 //Find free goal
							 for(Goal goal : client.getGoalListMap().get(bc)) {
								 Box bb = client.getCurrentSubState().boxes[goal.getPos().row][goal.getPos().col];

								 //Goal is free
								 if(bb == null || Character.toLowerCase(bb.getLabel()) != goal.getLabel() && reachableGoals.contains(goal)) {
									 list.add(b);
									 listCount.add(goal.priority);
									 break;
								 }
							 }
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
		System.err.println(list);
		return list;
	}
}