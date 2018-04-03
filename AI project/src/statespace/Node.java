package statespace;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import statespace.AIClient.Agent;
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

	public Node parent;
	public Command action;
	
	private int g;
	private int h = Integer.MAX_VALUE;
	private AIClient client;
	
	private int _hash = 0;

	private Agent agent;

	private Pos required;

	public Node(Node parent, AIClient client, Agent agent) {
		this.parent = parent;
		this.client = client;
		this.agent = agent;
		boxes = new Box[client.getMaxRow()][client.getMaxCol()];
		if (parent == null) {
			this.g = 0;
		} else {
			this.g = parent.g() + 1;
		}
	}

	public int g() {
		return this.g;
	}
	
	public int h() {
		return this.h;
	}

	public boolean isInitialState() {
		return this.parent == null;
	}

	public boolean requestFulfilled(LinkedList<Pos> pos) {
		boolean empty = true;
		for(Pos p : pos) {
			if (p != null && !isEmpty(p)) {
				empty = false;
				break;
			}
		}
		return empty;
 		
	}
	public boolean isGoalState() {
//		for (Goal goal : client.getGoalList()) {
//			if (goal.hasBox())
//				System.err.println(goal.getLabel() + " : " + goal.getBox().getLabel());
//			if (!goal.hasBox()) {
//				return false;	
//			}
//		}
		
//		for (Goal goal : client.getGoalList()) {
//			Box goalBox = this.boxes[goal.getPos().x][goal.getPos().y]; 
//			if (goalBox == null)
//				return false;
//			if (Character.toLowerCase(goalBox.getLabel()) != goal.getLabel())
//				return false;
//		}

		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				char g = client.getGoals(agent)[row][col] != null ? client.getGoals(agent)[row][col].getLabel() : 0;
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
					Node n = this.ChildNode();
					n.action = c;
					n.agentRow = newAgentRow;
					n.agentCol = newAgentCol;
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
						Node n = this.ChildNode();
						n.action = c;
						n.agentRow = newAgentRow;
						n.agentCol = newAgentCol;
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
						Node n = this.ChildNode();
						n.action = c;
						n.agentRow = newAgentRow;
						n.agentCol = newAgentCol;
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

	private boolean cellIsFree(int row, int col) {
		return !client.getWalls()[row][col];
	}
	
	public boolean isEmpty(Pos pos) {
		return boxes[pos.row][pos.col] == null && !(agentRow == pos.row && agentCol == pos.col);
	}
	private boolean boxAt(int row, int col) {
		return this.boxes[row][col] != null;
	}

	private Node ChildNode() {
		Node copy = new Node(this, client, agent);
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
			if (!client.getWalls()[row][0]) {
				break;
			}
			for (int col = 0; col < client.getMaxCol(); col++) {
				if (this.boxes[row][col] != null) {
					s.append(this.boxes[row][col]);
				} else if ((client.getGoals(agent)[row][col] != null ? client.getGoals(agent)[row][col].getLabel() : 0) > 0) {
					s.append(client.getGoals(agent)[row][col]);
				} else if (client.getWalls()[row][col]) {
					s.append("+");
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
	
	public void calculateDistanceToGoal() {
		
		int distanceToGoals = 0;
		int distanceToBoxes = 0;
		// Distance from agent to the  nearest box
		int distanceToNearestBox = 0;
		
		// Calculate manhattan distance from a box to the nearest goal for that box, which is not occupied.
		// Iterate through the grid
		for (int row = 1; row < client.getMaxRow() - 1; row++) {
			for (int col = 1; col < client.getMaxCol() - 1; col++) {
				
				Box box = this.boxes[row][col];
				boolean boxInGoal = false;
				
				// Check if there is a box in the cell
				// If not, continue
				if (box != null) {
					
					// Get the label of the box
					char label = Character.toLowerCase(box.getLabel());
					
					List<Goal> freeGoals = new ArrayList<Goal>();
					
					// DO NOT USE BELOW CODE, REWRITE PLEASE!!
					// Find all goals matching the box by its label
//					if (client.getGoalMap().containsKey(label)) {
//						List<Goal> goals = client.getGoalMap().get(label);
//						
//						// Find all free goals for that box
//						for (Goal goal : goals) {
//							Box goalBox = this.boxes[goal.getPos().x][goal.getPos().y]; 
//							if (goalBox == null) {
//								freeGoals.add(goal);
//							} else {
//								// Check if the box are already in goal
//								if (goalBox == box) 
//									boxInGoal = true;
//								if (Character.toLowerCase(goalBox.getLabel()) != goal.getLabel())
//									freeGoals.add(goal);
//							}
//						}
//					}
					
					int distanceToNearestGoal = 0;
					// If the box is not in goal, and some goals are left open
					// Then find the nearest goal
//					if (!freeGoals.isEmpty() && !boxInGoal) {
//						
//						for (Goal goal : freeGoals) {
//							int distanceToGoal = goal.getDistanceToGoal(new Point(row,col));
//							if (distanceToGoal < distanceToNearestGoal || distanceToNearestGoal == 0)
//								distanceToNearestGoal = distanceToGoal;
//						}
//					
//						distanceToGoals += Math.pow(distanceToNearestGoal,1);
//					}
					
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

		// Calculate the amount of goals reached
		double goalScore = client.getGoalList().size();
		
		for (Goal goal : client.getGoalList()) {
			Box goalBox = this.boxes[goal.getPos().x][goal.getPos().y]; 
			if (goalBox != null)
				if (Character.toLowerCase(goalBox.getLabel()) == goal.getLabel())
					goalScore -= 1;
		}
		
		int distanceToGoalsSum = (int) (distanceToGoals * distanceFactor); 
		
		int distanceToNearestBoxSum = (int) (distanceToNearestBox * agentFactor);
		int distanceToAllBoxesSum = (int) (distanceToBoxes * agentFactor); // Not in use right now
		
		int goalScoreSum = (int) (goalScore * goalFactor);
		
		this.h = distanceToGoalsSum + distanceToNearestBoxSum + goalScoreSum;
	}

	public Pos getRequired() {
		return required;
	}

	public Node copy() {
		Node n = new Node(null, client, agent);

		
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