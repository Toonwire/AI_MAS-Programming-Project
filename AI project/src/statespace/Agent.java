package statespace;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import sampleclients.MultiCommand;

public class Agent {
	public static Agent wait;
	char id;
	String color;
	public ArrayList<Box> reachableBoxes = new ArrayList<>();;
	public ArrayList<Goal> reachableGoals = new ArrayList<>();;
	public Agent isHelping = null;
	public Agent getsHelp = null;
	public boolean getHelp = true;
	public int waiting = 0;
	
	public AIClient client;
	
	public Agent(char id, String color, AIClient client) {
		this.id = id;
		this.color = color;
		this.client = client;
		reachableBoxes = new ArrayList<>();
		reachableGoals = new ArrayList<>();
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
	
	private boolean goalIsBlocking(Goal goal, int i) {
		
		if (i-- < 0) return false;
		
		boolean blocked = false;
		boolean goalBlocked;
		
		for (; i >= 0; i--) {
			for (int j = 0; j <  client.getGoalPriorityList().get(i).size(); j++) {
				goalBlocked = true;
				
				Goal g = client.getGoalPriorityList().get(i).get(j);
				
				Box maybeBox = client.getCurrentSubState().boxes[g.getPos().row][g.getPos().col];
				
				if (maybeBox != null && Character.toLowerCase(maybeBox.getLabel()) == g.getLabel())
					continue;
				
				Pos p = g.getPos();
				Pos r = null;
				Pos l = null;
				
				ArrayList<Pos> alley = new ArrayList<>();
				
				if (AIClient.oneway(p)) {
					
					if (p.row - 1 > 0 && !AIClient.walls[p.row - 1][p.col]) {
						Pos c = new Pos(p.row - 1, p.col );
						
						r = c;
					} 
					
					if (p.row + 1 < AIClient.getMaxRow() && !AIClient.walls[p.row + 1][p.col]) {
						Pos c = new Pos(p.row + 1, p.col);
						
						if (r == null) r = c; 
						else l = c;
					} 
						
					
					if (p.col - 1 > 0 && !AIClient.walls[p.row][p.col - 1]) {
						Pos c = new Pos(p.row, p.col - 1);
						
						if (r == null) r = c; 
						else l = c;
					}
					
					if (p.col + 1 < AIClient.getMaxCol() && !AIClient.walls[p.row][p.col + 1]) {
						Pos c = new Pos(p.row, p.col + 1);
						
						if (r == null) r = c; 
						else l = c;
					}
					
					if (r != null) alley.add(r);
					if (l != null) alley.add(l);
				} else {
					goalBlocked = false;
					break;
				}
				
				while(r != null) {
					
					// Break if we have to pass current goal
					if (r.equals(goal.getPos()))
						break;
					
					// If r is a oneway
					if (AIClient.oneway(r)) {
						
						// Find next step or break if at a dead end
						if (r.row - 1 > 0 && !AIClient.walls[r.row - 1][r.col] && !alley.contains(new Pos(r.row - 1, r.col)))
							r = new Pos(r.row - 1, r.col );
						else if (r.row + 1 < AIClient.getMaxRow() && !AIClient.walls[r.row + 1][r.col] && !alley.contains(new Pos(r.row + 1, r.col)))
							r = new Pos(r.row + 1, r.col);
						else if (r.col - 1 > 0 && !AIClient.walls[r.row][r.col - 1] && !alley.contains(new Pos(r.row, r.col - 1)))
							r = new Pos(r.row, r.col - 1);
						else if (r.col + 1 < AIClient.getMaxCol() && !AIClient.walls[r.row][r.col + 1] && !alley.contains(new Pos(r.row, r.col + 1)))
							r = new Pos(r.row, r.col + 1);
						else
							break;
						
						alley.add(r);
						
					// Otherwise we must have found a way out of the alley
					} else {
						goalBlocked = false;
						break;
					}
				}
				
				while(l != null) {
					
					// Break if we have to pass current goal
					if (r.equals(goal.getPos()))
						break;
					
					// If r is a oneway
					if (AIClient.oneway(l)) {
						
						// Find next step or break if at a dead end
						if (l.row - 1 > 0 && !AIClient.walls[l.row - 1][l.col] && !alley.contains(new Pos(l.row - 1, l.col)))
							l = new Pos(l.row - 1, l.col );
						else if (l.row + 1 < AIClient.getMaxRow() && !AIClient.walls[l.row + 1][l.col] && !alley.contains(new Pos(l.row + 1, l.col)))
							l = new Pos(l.row + 1, l.col);
						else if (l.col - 1 > 0 && !AIClient.walls[l.row][l.col - 1] && !alley.contains(new Pos(l.row, l.col - 1)))
							l = new Pos(l.row, l.col - 1);
						else if (l.col + 1 < AIClient.getMaxCol() && !AIClient.walls[l.row][l.col + 1] && !alley.contains(new Pos(l.row, l.col + 1)))
							l = new Pos(l.row, l.col + 1);
						else 
							break;
						
						alley.add(l);
						
					// Otherwise we must have found a way out of the alley
					} else {
						goalBlocked = false;
						break;
					}
				}
				
				if (goalBlocked) {
					blocked = true;
				}
			}
		}
		return blocked;
	}
	
	public ArrayList<Box> getBoxesNotInGoal() {
		ArrayList<Box> list = new ArrayList<Box>();

		ArrayList<Integer> priorityCount = new ArrayList<Integer>();
		ArrayList<Integer> distanceCount = new ArrayList<Integer>();
		
		int priorityIndexReached = -1;
		for (int i = 0; i < client.getGoalPriorityList().size(); i++) {
			for (int j = 0; j < client.getGoalPriorityList().get(i).size(); j++) {
				Goal goal = client.getGoalPriorityList().get(i).get(j);
				
				boolean boxFound = false;
				for (Box someBox : reachableBoxes) {
					
					if ((someBox.goal == null || (someBox.goal != null && !someBox.equals(client.getCurrentSubState().boxes[someBox.goal.getPos().row][someBox.goal.getPos().col])) && Character.toLowerCase(someBox.getLabel()) == goal.getLabel())) {
						boxFound = true;
						break;
					}
				}
				
				// Check if a box is on top of the goal - and if this box match the goals label
				Box maybeBox = client.getCurrentSubState().boxes[goal.getPos().row][goal.getPos().col];
				
				if (reachableGoals.contains(goal) && boxFound && (maybeBox == null || Character.toLowerCase(maybeBox.getLabel()) != goal.getLabel()) && !goalIsBlocking(goal, i)) {
					priorityIndexReached = i;
					break;
				}
			}
			if (priorityIndexReached >= 0) break;
		}
		
		if (priorityIndexReached == -1 )
			return list;
			
		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				 Box b = client.getCurrentSubState().boxes[row][col];
				 Goal g = client.getGoals()[row][col];

				//Box is reachable and not in use
				if (b != null && !b.inWorkingProcess && reachableBoxes.contains(b)) {
					
					char bc = Character.toLowerCase(b.getLabel());
					
					//Box is not already in goal
					if((g == null || bc != g.getLabel())) {
			
						if (client.getGoalListMap().containsKey(bc)) {
							 
							 ArrayList<Goal> goals = client.getGoalListMap().get(bc);
							 
							 boolean boxReady = false;
							 int priority = Integer.MAX_VALUE;
							 int distance = Integer.MAX_VALUE;
							 
							 for (Goal goal : goals) {
								 Box maybeBox = client.getCurrentSubState().boxes[goal.getPos().row][goal.getPos().col];
							
							     if (reachableGoals.contains(goal) && client.getGoalPriorityList().get(priorityIndexReached).contains(goal) && goal.priority < priority && (maybeBox == null || Character.toLowerCase(maybeBox.getLabel()) != goal.getLabel())) {
									 priority = goal.priority;
									 boxReady = true;
									 									 
									 Integer[][] dijkstra = client.getDijkstraMap().get(goal);
									 if (dijkstra[row][col] < distance)
										 distance = dijkstra[row][col];
								 }
							 }
							 
							 if (boxReady) {
								 list.add(b);
								 priorityCount.add(priority);
								 distanceCount.add(distance);								 
							 }
						 }
					}
				}
			}
		}
		
		// Sort boxes depending on the goals that has highest priority
		for (int i = 0; i < list.size(); i++) {
			for (int j = 0; j < list.size() - 1; j++) {
				if (priorityCount.get(j) > priorityCount.get(j + 1)) {
					Integer tempPriority = priorityCount.get(j);
					priorityCount.remove(j);
					priorityCount.add(j + 1, tempPriority);
					
					Box temp = list.get(j);
					list.remove(j);
					list.add(j+1, temp);
				}
			}
		}
		
		// Sort boxes (swap only same label boxes), so that the closes boxes to a goal is first
		for (int i = 0; i < list.size(); i++) {
			for (int j = 0; j < list.size() - 1; j++) {
				if (distanceCount.get(j) > distanceCount.get(j + 1) && list.get(j).getLabel() == list.get(j + 1).getLabel()) {
					Integer tempDistance = distanceCount.get(j);
					distanceCount.remove(j);
					distanceCount.add(j + 1, tempDistance);
					
					Box temp = list.get(j);
					list.remove(j);
					list.add(j+1, temp);
				}
			}
		}
		return list;
	}
}