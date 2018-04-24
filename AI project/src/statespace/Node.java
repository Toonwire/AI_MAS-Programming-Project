package statespace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import statespace.Command;
import statespace.Command.Type;

public class Node {
	private static final Random RND = new Random(1);

	public int agentRow;
	public int agentCol;
	
	// Arrays are indexed from the top-left of the level, with first index being row and second being column.
	// Row 0: (0,0) (0,1) (0,2) (0,3) ...
	// Row 1: (1,0) (1,1) (1,2) (1,3) ...
	// Row 2: (2,0) (2,1) (2,2) (2,3) ...
	// ...
	// (Start in the top left corner, first go down, then go right)
	// E.g. this.walls[2] is an array of booleans having size MAX_COL.
	// this.walls[row][col] is true if there's a wall at (row, col)
	//

	public Box[][] boxes;
	public Goal[][] goals;
	
	public Pos agentGoal;
	public LinkedList<Pos> requestedPositions; 
	
	public Node parent;
	public Command action;
	
	private int g;
	private int h;
	private AIClient client;
	
	private int _hash = 0;

	private Agent agent;

	private Pos required;

	public boolean ignore = true;
	public Agent help = null;

	public Pos boxPosition;
	
	public Node(Node parent, AIClient client, Agent agent, Pos agentPos, Command action) {
		this.parent = parent;
		this.client = client;
		this.agent = agent;
		this.agentRow = agentPos.row;
		this.agentCol = agentPos.col;
		this.action = action;
		this.goals = new Goal[client.getMaxRow()][client.getMaxCol()];
		this.boxes = new Box[client.getMaxRow()][client.getMaxCol()];
		
		this.agentGoal = parent == null ? null : parent.agentGoal;
		
		this.g = parent == null ? 0 : parent.g() + 1;
//		this.requestedPositions = parent == null ? null : parent.requestedPositions;
		
//		//Box penalty
//		Box box = client.getCurrentSubState().boxes[required.row][required.col];
//		if(box != null && !box.getColor().equals(agent.getColor())) {
//			this.g += 5;
//		}
//		//Agent penalty
//		Agent ag = client.getCurrentSubState().agents[required.row][required.col];
//		if(ag != null) {
//			this.g += 5;
//		}
//		//Goal penalty
//		Goal g = client.getGoals()[required.row][required.col];
//		if(g != null && goals[required.row][required.col] == null) {
//			this.g += 5;
//		}
	}
	
	public int g() {
		int penalty = 0;
		if(required != null) {
			Box box = client.getCurrentSubState().boxes[required.row][required.col];
			if (box != null && !box.getColor().equals(agent.getColor())) {
				penalty += 5;
			} 
			if (client.getCurrentSubState().agents[required.row][required.col] != null)
				penalty += 5;
			Goal goal = client.getGoals()[required.row][required.col];
			if(goal != null && goals[required.row][required.col] != null) {
				penalty += 5;
			}
		}
		return this.g + penalty;
	}
	
	public int h() {

		int penalty = 0;
		return this.h > 0 ? this.h + penalty : this.calculateDistanceToGoal() + penalty;
	}

	public boolean isInitialState() {
		return this.parent == null;
	}

	public boolean isSubGoalState(Node node) {
		return this.equals(node);
	}	
	
	public boolean requestFulfilled(LinkedList<Pos> pos) {
		for(Pos p : pos) {
			if (p != null && !isEmpty(p)) {
				return false;
			}
		}
		return true;
 		
	}
	
	public boolean isGoalState() {
		if (boxPosition != null){
			return boxes[boxPosition.row][boxPosition.col] == null;
		}
		// Check for positions, if another agents has requested something
		if (requestedPositions != null) {
			
			boolean empty = true;
			for(Pos p : requestedPositions) {
				if (p != null && !isEmpty(p)) {
					empty = false;
					break;
				}
			}
			
			return empty; // && parent != null && parent.isEmpty(requestedPositions.get(1));
		}
		
		// Check if agents is placed correct, if he is trying to find a solutions to a conflict, without asking for help
		if (agentGoal != null && (agentGoal.row != agentRow || agentGoal.col != agentCol)) return false; 
		
		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				char g = goals[row][col] != null ? goals[row][col].getLabel() : 0;
				char b = boxes[row][col] != null ? Character.toLowerCase(boxes[row][col].getLabel()) : 0;
				if (g > 0 && b != g && client.getColor(g) != agent.getColor()) {
					return false;
				}
			}
		}
		return true;
	}

	public ArrayList<Node> getExpandedNodes() {
		ArrayList<Node> expandedNodes = new ArrayList<Node>(Command.EVERY.length);
		for (Command c : Command.EVERY) {
			// Determine applicability of action
			int newAgentRow = this.agentRow + Command.dirToRowChange(c.dir1);
			int newAgentCol = this.agentCol + Command.dirToColChange(c.dir1);

			if (c.actionType == Type.Move) {
				// Check if there's a wall or box on the cell to which the agent is moving
				if (this.cellIsFree(newAgentRow, newAgentCol)) {
					Node n = this.ChildNode(newAgentRow, newAgentCol, c);
//					n.action = c;
//					n.agentRow = newAgentRow;
//					n.agentCol = newAgentCol;
					n.required = new Pos(newAgentRow, newAgentCol);
					expandedNodes.add(n);
				}
			} else if (c.actionType == Type.Push) {
				// Make sure that there's actually a box to move
				if (this.boxAt(newAgentRow, newAgentCol)) {
					int newBoxRow = newAgentRow + Command.dirToRowChange(c.dir2);
					int newBoxCol = newAgentCol + Command.dirToColChange(c.dir2);
					// .. and that new cell of box is free
					if (this.cellIsFree(newBoxRow, newBoxCol)) {
						Node n = this.ChildNode(newAgentRow, newAgentCol, c);
//						n.action = c;
//						n.agentRow = newAgentRow;
//						n.agentCol = newAgentCol;
						n.boxes[newBoxRow][newBoxCol] = this.boxes[newAgentRow][newAgentCol];
						n.boxes[newAgentRow][newAgentCol] = null;
						n.required = new Pos(newBoxRow, newBoxCol);
						expandedNodes.add(n);
					}
				}
			} else if (c.actionType == Type.Pull) {
				if (this.cellIsFree(newAgentRow, newAgentCol)) {
					int boxRow = this.agentRow + Command.dirToRowChange(c.dir2);
					int boxCol = this.agentCol + Command.dirToColChange(c.dir2);
					if (this.boxAt(boxRow, boxCol)) {
						Node n = this.ChildNode(newAgentRow, newAgentCol, c);
//						n.action = c;
//						n.agentRow = newAgentRow;
//						n.agentCol = newAgentCol;
						n.boxes[this.agentRow][this.agentCol] = this.boxes[boxRow][boxCol];
						n.boxes[boxRow][boxCol] = null;
						n.required = new Pos(newAgentRow, newAgentCol);
						
						expandedNodes.add(n);
					}
				}
			}
		}
		Collections.shuffle(expandedNodes, RND);
		return expandedNodes;
	}

	public boolean cellIsFree(int row, int col) {
//		if(!ignore) {
//			Agent helper = agent.isHelping;
//			Box box = client.getCurrentSubState().boxes[row][col];
//			if(box != null && !box.getColor().equals(agent.getColor())) {
////				for(Agent helper : agent.getHelpers()) {
//					if(helper.getReachableBoxes().contains(box)) {
//						return false;
//					}
////				}
//				return true;
//			}
//			
//			Agent a = client.getCurrentSubState().agents[row][col];
//			if(a != null) {
////				for(Agent helper : agent.getHelpers()) {
//					if(helper == a) {
//						return false;
//					}
////				}
//				return true;
//			}	
//
////			return !client.getWalls()[row][col];
//		}
		boolean helping = true;
		if(!ignore && agent.isHelping != null) {
			helping = client.getCurrentSubState().agents[row][col] != agent.isHelping
					&& client.getCurrentState().agents[row][col] != agent.isHelping
					&& !agent.isHelping.getReachableBoxes().contains(client.getCurrentSubState().boxes[row][col])
					&& !agent.isHelping.getReachableBoxes().contains(client.getCurrentState().boxes[row][col]);
		}

		return !client.getWalls()[row][col] && boxes[row][col] == null && helping;
	}
	
	public boolean isEmpty(Pos pos) {
		return boxes[pos.row][pos.col] == null && !(agentRow == pos.row && agentCol == pos.col);
	}
	private boolean boxAt(int row, int col) {
		return this.boxes[row][col] != null;
	}

	private Node ChildNode(int r, int c, Command action) {
		Node copy = new Node(this, client, agent, new Pos(r, c), action);
		copy.goals = goals;
		copy.ignore = ignore;
		copy.help = help;
		copy.boxPosition = boxPosition;
		copy.requestedPositions = requestedPositions;
		for (int row = 0; row < client.getMaxRow(); row++) {
			System.arraycopy(this.boxes[row], 0, copy.boxes[row], 0, client.getMaxCol());
		}
		return copy;
	}

	public LinkedList<Node> extractPlan() {
		LinkedList<Node> plan = new LinkedList<Node>();
		Node n = this;
		while (!n.isInitialState()) {
			plan.addFirst(n);
			n = n.parent;
		}
		return plan;
	}

	@Override
	public int hashCode() {
		if (this._hash == 0) {
			final int prime = 31;
			int result = 1;
			result = prime * result + this.agentCol;
			result = prime * result + this.agentRow;
			result = prime * result + Arrays.deepHashCode(this.boxes);
			this._hash = result;
		}
		return this._hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;
		Node other = (Node) obj;
		if (this.agentRow != other.agentRow || this.agentCol != other.agentCol)
			return false;
		if (!Arrays.deepEquals(this.boxes, other.boxes))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		for (int row = 0; row < client.getMaxRow(); row++) {
//			if (!client.getWalls()[row][0]) {
//				break;
//			}
			for (int col = 0; col < client.getMaxCol(); col++) {
				if (agentGoal != null && agentGoal.row == row && agentGoal.col == col) {
					s.append("L");
				} else if (client.getWalls()[row][col]) {
					s.append("+");
				} else if (client.getTempWalls()[row][col]) {
					s.append("+");
				} else if (this.boxes[row][col] != null) {
					s.append(this.boxes[row][col]);
				} else if ((goals[row][col] != null ? goals[row][col].getLabel() : 0) > 0) {
					s.append(goals[row][col]);
				} else if (row == this.agentRow && col == this.agentCol) {
					s.append(agent.getLabel());
				} else {
					s.append(" ");
				}
			}
			s.append("\n");
		}
		return s.toString();
	}
	
public int calculateDistanceToGoal() {
		
		int distanceToGoals = 0;
		int distanceToBoxes = 0;
		// Distance from agent to the  nearest box
		int distanceToNearestBox = 0;
		
		int distanceToAgentGoal = 0;
		
		// Calculate manhattan distance from a box to the nearest goal for that box, which is not occupied.
		// Iterate through the grid
		
		List<Goal> freeGoals = new ArrayList<Goal>();
		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				Goal goal = goals[row][col];
				Box box = boxes[row][col];
				
				if (box == null && goal != null) {
					freeGoals.add(goal);
				} else if (goal != null && Character.toLowerCase(box.getLabel()) == goal.getLabel()) {
					freeGoals.add(goal);
				}
			}
		}
		
		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				
				Box box = this.boxes[row][col];
				boolean boxInGoal = false;
				
				// Check if there is a box in the cell
				// If not, continue
				if (box != null) {
					
					int distanceToNearestGoal = 0;
					
					Goal goalForBox = this.goals[row][col];
					
					if (goalForBox != null && Character.toLowerCase(box.getLabel()) == goalForBox.getLabel()) {
						boxInGoal = true;
					}
					
					// If the box is not in goal, and some goals are left open
					// Then find the nearest goal
					if (!freeGoals.isEmpty() && !boxInGoal) {
						
						for (Goal goal : freeGoals) {
							if (Character.toLowerCase(box.getLabel()) == goal.getLabel()) {
								
								int distanceToGoal = agentGoal != null ? goal.getPos().manhattanDistanceToPos(new Pos(row,col)) :
													 requestedPositions != null ? 0 :
													 client.getDijkstraMap().get(goal)[row][col];
								
								if (distanceToGoal < distanceToNearestGoal || distanceToNearestGoal == 0)
									distanceToNearestGoal = distanceToGoal;
							}
						}
					
						distanceToGoals += Math.pow(distanceToNearestGoal,1);
					}
					
					// Calculate the distance to the nearest box from the agent, which is not in a goal state
					int distanceToCurrentBox = Math.abs(row - agentRow) + Math.abs(col - agentCol);
					
					if (!boxInGoal)
						distanceToBoxes += distanceToCurrentBox;
					
					if ((distanceToCurrentBox < distanceToNearestBox || distanceToNearestBox == 0) && !boxInGoal) {
						distanceToNearestBox = distanceToCurrentBox;
					} 
				}
			}
		}
		
		// Set factors for measurements
		double goalFactor = 10;
		double agentFactor = 0.5;
		double distanceFactor = 1.0;
		double agentGoalFactor = 1.0;

		// Calculate the amount of goals missing
		double goalScore = client.getGoalList().size();
		
		if (freeGoals.isEmpty() && agentGoal != null) {
			distanceToAgentGoal = (int) ((Math.abs(agentGoal.row - agentRow) + Math.abs(agentGoal.col - agentCol)) * agentGoalFactor);
		} else {
			if (agentGoal != null) distanceToAgentGoal = client.getMaxRow() + client.getMaxCol();
			for (Goal goal : freeGoals) {
				Box goalBox = this.boxes[goal.getPos().row][goal.getPos().col]; 
				if (goalBox != null)
					if (Character.toLowerCase(goalBox.getLabel()) == goal.getLabel())
						goalScore -= 1;
			}
		}
		
		int distanceToGoalsSum = (int) (distanceToGoals * distanceFactor); 
		
		int distanceToNearestBoxSum = (int) (distanceToNearestBox * agentFactor);
		int distanceToAllBoxesSum = (int) (distanceToBoxes * agentFactor); // Not in use right now
		
		int goalScoreSum = (int) (goalScore * goalFactor);
		
		return distanceToGoalsSum + distanceToNearestBoxSum + goalScoreSum + distanceToAgentGoal;
	}

	public Pos getRequired() {
		return required;
	}

	public Node copy() {
		Node n = new Node(null, client, agent, new Pos(agentRow, agentCol), action);

		
		for (int row = 0; row < client.getMaxRow(); row++) {
			System.arraycopy(boxes[row], 0, n.boxes[row], 0, client.getMaxCol());
		}
		
		n.agentRow = agentRow;
		n.agentCol = agentCol;
		n.action = action;
		n.goals = goals;
		
		return n;
	}

	public Agent getAgent() {
		return agent;
	}
}