#!/bin/bash

# ============================================
# Script de Desenvolvimento Local
# ============================================

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

is_port_in_use() {
    ss -tlnp 2>/dev/null | grep -q ":$1 " && return 0 || return 1
}

get_pid() {
    ss -tlnp 2>/dev/null | grep ":$1 " | grep -oP 'pid=\K[0-9]+'
}

start_service() {
    local name=$1
    local port=$2
    local dir=$3
    
    if is_port_in_use $port; then
        echo -e "${CYAN}⏭️  $name já está rodando${NC}"
        return 0
    fi
    
    echo -e "${YELLOW}🚀 Iniciando $name...${NC}"
    cd "packages/${dir}" || return 1
    nohup mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=${port}" > "/tmp/${dir}.log" 2>&1 &
    cd ../..
    echo -e "${GREEN}✅ $name iniciado!${NC}"
}

start_service_debug() {
    local name=$1
    local port=$2
    local dir=$3
    local debug_port=$4
    
    if is_port_in_use $port; then
        echo -e "${CYAN}⏭️  $name já está rodando${NC}"
        return 0
    fi
    
    echo -e "${YELLOW}🚀 Iniciando $name com DEBUG na porta $debug_port...${NC}"
    cd "packages/${dir}" || return 1
    nohup mvn spring-boot:run \
        -Dspring-boot.run.arguments="--server.port=${port}" \
        -Dmaven.surefire.debug="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=${debug_port}" \
        > "/tmp/${dir}-debug.log" 2>&1 &
    cd ../..
    echo -e "${GREEN}✅ $name iniciado em modo DEBUG!${NC}"
    echo -e "${CYAN}   Debug port: $debug_port${NC}"
    echo -e "${CYAN}   Logs: /tmp/${dir}-debug.log${NC}"
}

stop_port() {
    local port=$1
    local name=$2
    local pid=$(get_pid $port)
    if [ -n "$pid" ]; then
        kill $pid 2>/dev/null && echo -e "${GREEN}✅ $name parado${NC}" || echo -e "${RED}❌ Erro${NC}"
    else
        echo -e "${CYAN}⏭️  $name não está rodando${NC}"
    fi
}

show_status() {
    echo ""
    echo "========== STATUS =========="
    is_port_in_use 8761 && echo "🟢 8761 - Eureka Server" || echo "🔴 8761 - Eureka Server"
    is_port_in_use 8080 && echo "🟢 8080 - API Gateway" || echo "🔴 8080 - API Gateway"
    is_port_in_use 8081 && echo "🟢 8081 - User Service" || echo "🔴 8081 - User Service"
    is_port_in_use 8082 && echo "🟢 8082 - Todo Service" || echo "🔴 8082 - Todo Service"
    is_port_in_use 3000 && echo "🟢 3000 - Frontend" || echo "🔴 3000 - Frontend"
    echo ""
    docker ps --format "🐳 {{.Names}} - {{.Status}}" 2>/dev/null
    echo ""
}

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}   TODO MONOREPO - DEV LOCAL           ${NC}"
echo -e "${CYAN}========================================${NC}"

while true; do
    echo ""
    echo "  [START]                [STOP]                 [DOCKER] "
    echo "  1) Eureka (8761)        6) Parar Eureka       d) Iniciar Docker (BD)"
    echo "  2) Gateway (8080)       7) Parar Gateway      d0) Parar Docker (BD)"
    echo "  3) User Svc (8081)      8) Parar User Svc"
    echo "  4) Todo Svc (8082)      9) Parar Todo Svc"
    echo "  5) Frontend (3000)     10) Parar Frontend"
    echo ""
    echo "  [DEBUG] - Subir com Debug (JDWP)"
    echo "  d1) User Svc DEBUG (porta 5005)"
    echo "  d2) Todo Svc DEBUG (porta 5006)"
    echo "  d3) User + Todo DEBUG"
    echo ""
    echo "  11) Subir TODOS"
    echo "  12) Parar TODOS"
    echo "  13) Status"
    echo "  14) Ver logs"
    echo ""
    echo "  15) Sair"
    echo ""
    read -p "Opção: " option
    
    case $option in
        1) start_service "Eureka" 8761 "eureka-server" ;;
        2) start_service "Gateway" 8080 "api-gateway" ;;
        3) start_service "User Service" 8081 "user-service" ;;
        4) start_service "Todo Service" 8082 "todo-service" ;;
        5) 
            if is_port_in_use 3000; then
                echo -e "${CYAN}⏭️  Frontend já está rodando${NC}"
            else
                echo -e "${YELLOW}🚀 Iniciando Frontend...${NC}"
                cd packages/web-app && nohup npm run dev > /tmp/frontend.log 2>&1 & cd ../..
                echo -e "${GREEN}✅ Frontend iniciado!${NC}"
            fi
            ;;
        6) stop_port 8761 "Eureka" ;;
        7) stop_port 8080 "Gateway" ;;
        8) stop_port 8081 "User Service" ;;
        9) stop_port 8082 "Todo Service" ;;
        10) stop_port 3000 "Frontend" ;;
        d1) start_service_debug "User Service" 8081 "user-service" 5005 ;;
        d2) start_service_debug "Todo Service" 8082 "todo-service" 5006 ;;
        d3)
            echo -e "${YELLOW}Subindo User + Todo em modo DEBUG...${NC}"
            start_service_debug "User Service" 8081 "user-service" 5005
            sleep 3
            start_service_debug "Todo Service" 8082 "todo-service" 5006
            echo ""
            echo -e "${GREEN}✅ Ambos iniciados em DEBUG!${NC}"
            echo -e "${CYAN}   User Service: localhost:8081 | Debug: localhost:5005${NC}"
            echo -e "${CYAN}   Todo Service: localhost:8082 | Debug: localhost:5006${NC}"
            ;;
        d)
            echo -e "${YELLOW}🚀 Iniciando Docker (Banco de Dados)...${NC}"
            docker compose up -d 2>/dev/null || docker-compose up -d 2>/dev/null
            echo -e "${GREEN}✅ Docker iniciado!${NC}"
            docker ps --format "🐳 {{.Names}} - {{.Status}}" 2>/dev/null
            ;;
        d0)
            echo -e "${YELLOW}🛑 Parando Docker (Banco de Dados)...${NC}"
            docker compose down 2>/dev/null || docker-compose down 2>/dev/null
            echo -e "${GREEN}✅ Docker parado!${NC}"
            ;;
        11)
            echo -e "${YELLOW}Subindo todos...${NC}"
            start_service "Eureka" 8761 "eureka-server"
            sleep 4
            start_service "User Service" 8081 "user-service"
            sleep 4
            start_service "Todo Service" 8082 "todo-service"
            sleep 4
            start_service "Gateway" 8080 "api-gateway"
            echo -e "${GREEN}✅ Todos iniciados!${NC}"
            ;;
        12)
            echo -e "${YELLOW}Parando todos...${NC}"
            stop_port 3000 "Frontend"
            stop_port 8080 "Gateway"
            stop_port 8082 "Todo Service"
            stop_port 8081 "User Service"
            stop_port 8761 "Eureka"
            ;;
        13) show_status ;;
        14)
            echo "1)Eureka 2)Gateway 3)User 4)Todo 5)Frontend"
            read -p "Opção: " logopt
            case $logopt in
                1) tail -f /tmp/eureka-server.log ;;
                2) tail -f /tmp/api-gateway.log ;;
                3) tail -f /tmp/user-service.log ;;
                4) tail -f /tmp/todo-service.log ;;
                5) tail -f /tmp/frontend.log ;;
            esac
            ;;
        15) echo "👋"; exit 0 ;;
        *) echo -e "${RED}❌ Inválido${NC}" ;;
    esac
done