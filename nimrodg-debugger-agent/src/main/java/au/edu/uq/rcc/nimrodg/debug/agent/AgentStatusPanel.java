/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2019 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.nimrodg.debug.agent;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.agent.messages.NetworkJob;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import javax.swing.table.AbstractTableModel;

public class AgentStatusPanel extends javax.swing.JPanel {

	private Agent m_Agent;

	public AgentStatusPanel() {
		initComponents();
		m_JobsTable.getTableHeader().setReorderingAllowed(false);
		m_JobsTable.setModel(new _TableModel());
		setAgent(null);
	}

	public final void setAgent(Agent agent) {
		m_Agent = agent;
		update();
	}

	public void update() {
		if(m_Agent == null) {
			m_QueueField.setText("");
			m_ShutdownReasonField.setText("");
			m_ShutdownSignalField.setText("");
			m_StateField.setText("");
			m_UUIDField.setText("");
			m_LastHeardFromField.setText("");
			setJob(null);
		} else {
			m_QueueField.setText(stringOrEmpty(m_Agent.getQueue()));
			m_ShutdownReasonField.setText(stringOrEmpty(m_Agent.getShutdownReason()));
			m_ShutdownSignalField.setText(stringOrEmpty(m_Agent.getShutdownSignal()));
			m_StateField.setText(stringOrEmpty(m_Agent.getState()));
			m_UUIDField.setText(stringOrEmpty(m_Agent.getUUID()));
			Instant ins = m_Agent.getLastHeardFrom();
			if(ins == null) {
				m_LastHeardFromField.setText("");
			} else {
				m_LastHeardFromField.setText(ins.toString());
			}
		}
	}

	public void setJob(NetworkJob job) {
		_TableModel model = (_TableModel)m_JobsTable.getModel();
		model.reset();

		if(job == null) {
			return;
		}

		for(int i = 0; i < job.numCommands; ++i) {
			model.addInstance(new AgentUpdate.Builder()
					.agentUuid(m_Agent.getUUID())
					.jobUuid(UUID.randomUUID())
					.commandResult(new AgentUpdate.CommandResult_(null, i, 0.0f, 0, "", 0))
					.action(AgentUpdate.Action.Continue)
					.build()
			);
		}

	}

	public void updateCommand(AgentUpdate au) {
		if(au == null) {
			return;
		}

		_TableModel model = (_TableModel)m_JobsTable.getModel();
		model.update(au);
		update();
	}

	private static String stringOrEmpty(Object o) {
		if(o == null) {
			return null;
		}

		return o.toString();
	}

	private class _TableModel extends AbstractTableModel {

		private final ArrayList<AgentUpdate> m_Instances;

		private final int ROW_INSTANCE_INDEX = 0;
		private final int ROW_STATUS = 1;
		private final int ROW_TIME = 2;
		private final int ROW_RETVAL = 3;
		private final int ROW_MESSAGE = 4;
		private final int ROW_ERROR_CODE = 5;
		private final int ROW_ACTION = 6;

		public _TableModel() {
			m_Instances = new ArrayList<>();
		}

		public void addInstance(AgentUpdate au) {
			if(au == null) {
				return;
			}

			m_Instances.add(au);
			fireTableRowsInserted(getRowCount(), getRowCount());
		}

		public void update(AgentUpdate au) {
			int row = (int)au.getCommandResult().index;
			m_Instances.set(row, au);
			fireTableRowsUpdated(row, row);
		}

		public void reset() {
			fireTableRowsDeleted(0, getRowCount());
			m_Instances.clear();
		}

		@Override
		public int getRowCount() {
			return m_Instances.size();
		}

		@Override
		public int getColumnCount() {
			return m_ColumnNames.length;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			AgentUpdate job = m_Instances.get(rowIndex);

			if(columnIndex == ROW_INSTANCE_INDEX) {
				return job.getCommandResult().index;
			} else if(columnIndex == ROW_STATUS) {
				return job.getCommandResult().status;
			} else if(columnIndex == ROW_TIME) {
				return job.getCommandResult().time;
			} else if(columnIndex == ROW_RETVAL) {
				return job.getCommandResult().retVal;
			} else if(columnIndex == ROW_MESSAGE) {
				return job.getCommandResult().message;
			} else if(columnIndex == ROW_ERROR_CODE) {
				return job.getCommandResult().errorCode;
			} else if(columnIndex == ROW_ACTION) {
				return job.getAction();
			}

			return null;
		}

		@Override
		public String getColumnName(int columnIndex) {
			return m_ColumnNames[columnIndex];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return m_ColumnTypes[columnIndex];
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return false;
		}

		private final Class[] m_ColumnTypes = new Class[]{
			Long.class, CommandResult.CommandResultStatus.class, Float.class, Integer.class, String.class, Integer.class, AgentUpdate.Action.class
		};

		private final String[] m_ColumnNames = new String[]{
			"Index", "State", "Time", "Return", "Message", "Error Code", "Action"
		};
	}

	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        m_StateField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel1 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel4 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel5 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel6 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel7 = new javax.swing.JLabel();
        m_ShutdownReasonField = new javax.swing.JTextField();
        m_ShutdownSignalField = new javax.swing.JTextField();
        m_UUIDField = new javax.swing.JTextField();
        m_QueueField = new javax.swing.JTextField();
        jScrollPane2 = new javax.swing.JScrollPane();
        m_JobsTable = new javax.swing.JTable();
        javax.swing.JLabel jLabel8 = new javax.swing.JLabel();
        m_LastHeardFromField = new javax.swing.JTextField();

        m_StateField.setEditable(false);

        jLabel1.setText("State");

        jLabel4.setText("Queue");

        jLabel5.setText("UUID");

        jLabel6.setText("Shutdown Signal");

        jLabel7.setText("Shutdown Reason");

        m_ShutdownReasonField.setEditable(false);

        m_ShutdownSignalField.setEditable(false);

        m_UUIDField.setEditable(false);

        m_QueueField.setEditable(false);

        m_JobsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null}
            },
            new String [] {
                "Index", "Status", "Time", "Return", "Message", "Action"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Long.class, java.lang.String.class, java.lang.Float.class, java.lang.Integer.class, java.lang.String.class, java.lang.Object.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane2.setViewportView(m_JobsTable);

        jLabel8.setText("Last Heard From");

        m_LastHeardFromField.setEditable(false);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addComponent(jLabel4)
                            .addComponent(jLabel5)
                            .addComponent(jLabel6)
                            .addComponent(jLabel7)
                            .addComponent(jLabel8))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(m_UUIDField, javax.swing.GroupLayout.DEFAULT_SIZE, 671, Short.MAX_VALUE)
                            .addComponent(m_QueueField)
                            .addComponent(m_StateField)
                            .addComponent(m_ShutdownReasonField, javax.swing.GroupLayout.DEFAULT_SIZE, 671, Short.MAX_VALUE)
                            .addComponent(m_ShutdownSignalField)
                            .addComponent(m_LastHeardFromField, javax.swing.GroupLayout.DEFAULT_SIZE, 671, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(m_StateField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(m_QueueField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(m_UUIDField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(m_ShutdownSignalField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(m_ShutdownReasonField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(m_LastHeardFromField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 144, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable m_JobsTable;
    private javax.swing.JTextField m_LastHeardFromField;
    private javax.swing.JTextField m_QueueField;
    private javax.swing.JTextField m_ShutdownReasonField;
    private javax.swing.JTextField m_ShutdownSignalField;
    private javax.swing.JTextField m_StateField;
    private javax.swing.JTextField m_UUIDField;
    // End of variables declaration//GEN-END:variables
}
