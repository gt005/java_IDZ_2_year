package Restaurant.Agents;

import Restaurant.Agents.OrderAgent;

import Restaurant.Addons.JsonFileHandler;

import Restaurant.Behaviors.RegisterInDFBehaviour;

import Restaurant.Items.Menu;
import Restaurant.Items.Parcers.CreateMenuFromJSON;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.*;
import jade.wrapper.ContainerController;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import jade.core.*;
import jade.domain.FIPAAgentManagement.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.RecursiveTask;

/**
 * Класс управляющего агента. Может быть только один экземпляр этого класса.
 */
public class SupervisorAgent extends Agent {
    private static SupervisorAgent instance;
    ContainerController jadeRuntimeContainer;

    private int ordersAmount = 0;

    private SupervisorAgent(int visitorsAmount, ContainerController jadeRuntimeContainer) {
        this.jadeRuntimeContainer = jadeRuntimeContainer;

        addBehaviour(new RegisterInDFBehaviour(this, "Supervisor", "Restaurant"));
        addBehaviour(new CreateMenu());
        addBehaviour(new InformMessageReceiver());
        addBehaviour(new RequestMessageReceiver());

        createVisitors(visitorsAmount);
    }

    /**
     * Возвращает экземпляр класса. Если экземпляр класса не был создан, то он будет создан.
     *
     * @return экземпляр класса
     */
    public static synchronized SupervisorAgent getInstance(int visitorsAmount, ContainerController jadeRuntimeContainer) {
        if (instance == null) {
            instance = new SupervisorAgent(visitorsAmount, jadeRuntimeContainer);
        }
        return instance;
    }

//    @Override
//    protected void setup() {
//        System.out.println("Supervisor agent " + getAID().getName() + " is ready.");
//    }

    /**
     * Поведение, которое обрабатывает request сообщения.
     */
    private class RequestMessageReceiver extends CyclicBehaviour {
        public void action() {
            // ожидаем сообщение
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
            if (msg != null) {
                String response = msg.getContent();
                ACLMessage message = new ACLMessage(ACLMessage.INFORM);
                message.addReceiver(msg.getSender());

                try {
                    if ("send_menu".equals(response)) {
                        message.setContentObject(Menu.getInstance());
                    } else if ("create_order_agent".equals(response)) {
                        AID orderAgentAID = createAgentByName("OrderAgent", "order " + ordersAmount);
                        if (orderAgentAID != null) {
                            message.setContentObject(orderAgentAID);
                            ordersAmount++;
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                send(message);
            } else {
                // если сообщения нет, то поведение блокируется на команде receive
                block();
            }
        }
    }

    /**
     * Отвечает за получение и обработку сообщений. Распределяет сообщения по типам и выполняет нужные методы.
     */
    private class InformMessageReceiver extends CyclicBehaviour {
        public void action() {
            // ожидаем сообщение
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));
            if (msg != null) {
                String response = msg.getContent();

                System.out.println("\n------------------\nSupervisor get\t" + response + " \n--------------------\n");

                DFAgentDescription dfd = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("Visitor");
                dfd.addServices(sd);

                try {
                    DFAgentDescription[] result = DFService.search(myAgent, dfd);
                    for (int i = 0; i < result.length; i++) {
                        AID aid = result[i].getName();
                        ACLMessage message = new ACLMessage(ACLMessage.INFORM);
                        message.addReceiver(aid);
                        message.setContent("from supervisor " + response + " " + i);
                        send(message);
                    }
                } catch (FIPAException ex) {
                    ex.printStackTrace();
                }
            } else {
                // если сообщения нет, то поведение блокируется на команде receive
                block();
            }
        }
    }

    /**
     * Загражает из файла и инициализирует объект меню.
     */
    private static class CreateMenu extends OneShotBehaviour {
        public void action() {
            try {
                CreateMenuFromJSON.create(
                        JsonFileHandler.readJsonFromFile(
                                "input_data/menu_dishes.txt"
                        )
                );
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Создает агентов посетителей.
     *
     * @param visitorsAmount количество агентов посетителей.
     */
    private void createVisitors(int visitorsAmount) {
        for (int i = 0; i < visitorsAmount; i++) {
            createAgentByName("VisitorAgent", "visitor " + i);
        }
    }

    /**
     * Создает агента по имени
     */
    private AID createAgentByName(String nameOfTypeOfAgent, String nameOfAgent) {
        final List<String> namesOfAgents = List.of(
                "VisitorAgent",
                "OrderAgent"
        );

        if (!namesOfAgents.contains(nameOfTypeOfAgent)) {
            System.out.println("Agent with name " + nameOfAgent + " not found");
            return null;
        }

        AgentController agentController = null;
        AID agentAID = null;
        try {
            agentController = jadeRuntimeContainer.createNewAgent(
                    nameOfAgent,
                    "Restaurant.Agents." + nameOfTypeOfAgent,
                    null
            );
            agentController.start();

            // Получаем AID агента
            String agentName = agentController.getName();
            agentAID = new AID(agentName, AID.ISGUID);
            agentAID.setName(agentName);
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }


        return agentAID;
    }

    protected void takeDown() {
        System.out.println("Supervisor agent " + getAID().getName() + " is terminating.");
    }
}
