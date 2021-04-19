/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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

	public AgentStatusPanel() {
		initComponents();
		jobsTable.getTableHeader().setReorderingAllowed(false);
		jobsTable.setModel(new _TableModel());
		this.update((Agent)null);
	}

	public final void update(Agent agent) {
		if(agent == null) {
			queueField.setText("");
			shutdownReasonField.setText("");
			shutdownSignalField.setText("");
			stateField.setText("");
			uuidField.setText("");
			lastHeardFromField.setText("");
			setJob(null);
		} else {
			queueField.setText(stringOrEmpty(agent.getQueue()));
			shutdownReasonField.setText(stringOrEmpty(agent.getShutdownReason()));
			shutdownSignalField.setText(stringOrEmpty(agent.getShutdownSignal()));
			stateField.setText(stringOrEmpty(agent.getState()));
			uuidField.setText(stringOrEmpty(agent.getUUID()));
			Instant ins = agent.getLastHeardFrom();
			if(ins == null) {
				lastHeardFromField.setText("");
			} else {
				lastHeardFromField.setText(ins.toString());
			}
		}
	}

	public void setJob(NetworkJob job) {
		_TableModel model = (_TableModel)jobsTable.getModel();
		model.reset();

		if(job == null) {
			return;
		}

		for(int i = 0; i < job.numCommands; ++i) {
			model.addInstance(new AgentUpdate.Builder()
					.agentUuid(UUID.randomUUID()) /* This doesn't matter here. */
					.timestamp(Instant.now())
					.jobUuid(UUID.randomUUID())
					.commandResult(new AgentUpdate.CommandResult_(null, i, 0.0f, 0, "", 0))
					.action(AgentUpdate.Action.Continue)
					.build()
			);
		}

	}

	public void updateCommand(Agent agent, AgentUpdate au) {
		if(au == null) {
			return;
		}

		_TableModel model = (_TableModel)jobsTable.getModel();
		model.update(au);
		update(agent);
	}

	private static String stringOrEmpty(Object o) {
		if(o == null) {
			return null;
		}

		return o.toString();
	}

	private class _TableModel extends AbstractTableModel {

		private final ArrayList<AgentUpdate> instances;

		private final int ROW_INSTANCE_INDEX = 0;
		private final int ROW_STATUS = 1;
		private final int ROW_TIME = 2;
		private final int ROW_RETVAL = 3;
		private final int ROW_MESSAGE = 4;
		private final int ROW_ERROR_CODE = 5;
		private final int ROW_ACTION = 6;

		public _TableModel() {
			instances = new ArrayList<>();
		}

		public void addInstance(AgentUpdate au) {
			if(au == null) {
				return;
			}

			instances.add(au);
			fireTableRowsInserted(getRowCount(), getRowCount());
		}

		public void update(AgentUpdate au) {
			int row = (int)au.getCommandResult().index;
			instances.set(row, au);
			fireTableRowsUpdated(row, row);
		}

		public void reset() {
			fireTableRowsDeleted(0, getRowCount());
			instances.clear();
		}

		@Override
		public int getRowCount() {
			return instances.size();
		}

		@Override
		public int getColumnCount() {
			return COLUMN_NAMES.length;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			AgentUpdate job = instances.get(rowIndex);

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
			return COLUMN_NAMES[columnIndex];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return COLUMN_TYPES[columnIndex];
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return false;
		}

		private final Class[] COLUMN_TYPES = new Class[]{
			Long.class, CommandResult.CommandResultStatus.class, Float.class, Integer.class, String.class, Integer.class, AgentUpdate.Action.class
		};

		private final String[] COLUMN_NAMES = new String[]{
			"Index", "State", "Time", "Return", "Message", "Error Code", "Action"
		};
	}

	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        stateField = new javax.swing.JTextField();
        javax.swing.JLabel jLabel1 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel4 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel5 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel6 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel7 = new javax.swing.JLabel();
        shutdownReasonField = new javax.swing.JTextField();
        shutdownSignalField = new javax.swing.JTextField();
        uuidField = new javax.swing.JTextField();
        queueField = new javax.swing.JTextField();
        jScrollPane2 = new javax.swing.JScrollPane();
        jobsTable = new javax.swing.JTable();
        javax.swing.JLabel jLabel8 = new javax.swing.JLabel();
        lastHeardFromField = new javax.swing.JTextField();

        stateField.setEditable(false);

        jLabel1.setText("State");

        jLabel4.setText("Queue");

        jLabel5.setText("UUID");

        jLabel6.setText("Shutdown Signal");

        jLabel7.setText("Shutdown Reason");

        shutdownReasonField.setEditable(false);

        shutdownSignalField.setEditable(false);

        uuidField.setEditable(false);

        queueField.setEditable(false);

        jobsTable.setModel(new javax.swing.table.DefaultTableModel(
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
        jScrollPane2.setViewportView(jobsTable);

        jLabel8.setText("Last Heard From");

        lastHeardFromField.setEditable(false);

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
                            .addComponent(uuidField, javax.swing.GroupLayout.DEFAULT_SIZE, 671, Short.MAX_VALUE)
                            .addComponent(queueField)
                            .addComponent(stateField)
                            .addComponent(shutdownReasonField, javax.swing.GroupLayout.DEFAULT_SIZE, 671, Short.MAX_VALUE)
                            .addComponent(shutdownSignalField)
                            .addComponent(lastHeardFromField, javax.swing.GroupLayout.DEFAULT_SIZE, 671, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(stateField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(queueField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(uuidField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(shutdownSignalField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(shutdownReasonField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lastHeardFromField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 144, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable jobsTable;
    private javax.swing.JTextField lastHeardFromField;
    private javax.swing.JTextField queueField;
    private javax.swing.JTextField shutdownReasonField;
    private javax.swing.JTextField shutdownSignalField;
    private javax.swing.JTextField stateField;
    private javax.swing.JTextField uuidField;
    // End of variables declaration//GEN-END:variables
}
