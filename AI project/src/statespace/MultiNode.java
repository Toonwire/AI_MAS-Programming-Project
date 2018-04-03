package statespace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import statespace.AIClient.Agent;
import statespace.Command.Type;


public class MultiNode {
	public Box[][] boxes;
	public Agent[][] agents;
	private Pos[] agentPos;
		
	public AIClient client;

	
	public MultiNode(AIClient client, Box[][] boxes, Agent[][] agents) {
		this.client = client;
		this.boxes = boxes;
		this.agents = agents;
		
		agentPos = new Pos[client.getAgentNum()];
		for(int row = 0; row < client.getMaxRow(); row++) {
			for(int col = 0; col < client.getMaxCol(); col++) {
				Agent agent = agents[row][col];
				if (agent != null) {
					agentPos[agent.getID()] = new Pos(row, col);
				}
			}
		}
	}

	public MultiNode(MultiNode parent, int i, Command command) {
		this.client = parent.client;
		this.boxes = new Box[client.getMaxRow()][client.getMaxCol()];
		this.agents = new Agent[client.getMaxRow()][client.getMaxCol()];
		agentPos = new Pos[client.getAgentNum()];
		
		//Copy
		for (int row = 0; row < client.getMaxRow(); row++) {
			System.arraycopy(parent.boxes[row], 0, this.boxes[row], 0, client.getMaxCol());
			System.arraycopy(parent.agents[row], 0, this.agents[row], 0, client.getMaxCol());
		}
		System.arraycopy(parent.agentPos, 0, this.agentPos, 0, parent.agentPos.length);

		//Change
		int agentRow = agentPos[i].row;
		int agentCol = agentPos[i].col;
		
		int newAgentRow = agentRow + Command.dirToRowChange(command.dir1);
		int newAgentCol = agentCol + Command.dirToColChange(command.dir1);

		//Move agent
//		System.err.println(""+command+" "+newAgentRow+" "+newAgentCol);
		agents[newAgentRow][newAgentCol] = agents[agentRow][agentCol];
		agents[agentRow][agentCol] = null;
		agentPos[i] = new Pos(newAgentRow, newAgentCol);

		//Move box
		if (command.actionType == Type.Push) {
			int newBoxRow = newAgentRow + Command.dirToRowChange(command.dir2);
			int newBoxCol = newAgentCol + Command.dirToColChange(command.dir2);
			boxes[newBoxRow][newBoxCol] = this.boxes[newAgentRow][newAgentCol];
			boxes[newAgentRow][newAgentCol] = null;	
			
		} else if (command.actionType == Type.Pull) {
			int boxRow = agentRow + Command.dirToRowChange(command.dir2);
			int boxCol = agentCol + Command.dirToColChange(command.dir2);
			boxes[agentRow][agentCol] = boxes[boxRow][boxCol];
			boxes[boxRow][boxCol] = null;
		}
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		for (int row = 0; row < client.getMaxRow(); row++) {
			if (!client.getWalls()[row][0]) {
				break;
			}
			for (int col = 0; col < client.getMaxCol(); col++) {
				if (boxes[row][col] != null) {
					s.append(this.boxes[row][col]);
				} else if (client.getGoals()[row][col] != null) {
					s.append(client.getGoals()[row][col]);
				} else if (client.getWalls()[row][col]) {
					s.append("+");
				} else if (agents[row][col] != null) {
					s.append(agents[row][col]);
				} else {
					s.append(" ");
				}
			}
			s.append("\n");
		}
		return s.toString();
	}

	public boolean isEmpty(Pos pos) {
		return (agents[pos.row][pos.col] == null) && (boxes[pos.row][pos.col] == null);
	}
}